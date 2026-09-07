package camchua.phoban.phobanpro.game;

import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.DebugLogger;
import camchua.phoban.phobanpro.utils.Messages;
import camchua.phoban.phobanpro.utils.Utils;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GameTask implements Runnable {

    private final Game game;
    private int countdown;
    private int waitingKick = 0;

    public GameTask(Game g) {
        this.game = g;
        this.countdown = getConfig().getInt("Settings.StartCountdown");
    }

    @Override
    public void run() {
        switch (game.getStatus()) {
            case WAITING -> handleWaiting();
            case STARTING -> handleStarting();
            case PLAYING -> handlePlaying();
        }
    }

    private void handleWaiting() {
        countdown = getConfig().getInt("Settings.StartCountdown");
        game.auditWaitingState();
        if (game.isFull()) {
            game.starting();
            waitingKick = 0;
            return;
        }
        if (!game.getPlayers().isEmpty()) {
            waitingKick++;
            sendWaitingStartWarning();
            if (waitingKick >= getConfig().getInt("Settings.WaitingKick", 600)) {
                logWaiting("waiting kick triggered after " + waitingKick + " seconds");
                game.forceKickAll("&c&lPhòng đã bị đóng do không bắt đầu quá lâu.");
                waitingKick = 0;
            }
        } else {
            waitingKick = 0;
        }
    }

    private void handleStarting() {
        waitingKick = 0;
        game.auditWaitingState();
        if (game.getPlayers().isEmpty()) {
            logWaiting("suspicious STARTING state with no players, returning to WAITING");
            game.setStatus(GameStatus.WAITING);
            countdown = getConfig().getInt("Settings.StartCountdown");
            return;
        }

        sendCountdown("StartCountdown");
        countdown--;
        game.nextStageParticle(false);

        if (countdown <= -1) {
            game.start();
            countdown = getConfig().getInt("Settings.StageCountdown");
        }
    }

    private void handlePlaying() {
        waitingKick = 0;
        if (game.isEntryWarmup()) {
            return;
        }
        sendTimeRemaining();

        int mobEndingGlow = getConfig().getInt("Settings.MobEndingGlow");
        if (game.getTimeLeft() <= mobEndingGlow) game.glowAllMob();

        handleRespawning();

        if (game.quitCountdown) {
            handleQuitCountdown();
            return;
        }

        game.time();
        game.nextStageParticle(true);
        game.checkSpawnMobs();

        if (game.getTimeLeft() <= 0) {
            handleTimeOut();
            return;
        }

        if (game.getPlayers().isEmpty()) {
            game.fullReset();
            return;
        }

        if (game.stageCountdown) {
            handleStageCountdown();
        } else {
            game.checkStage();
        }
    }

    private void handleTimeOut() {
        if (game.getStatus() != GameStatus.PLAYING) {
            return;
        }
        List<Player> snapshot = new ArrayList<>(game.getPlayers());
        for (Player p : snapshot) {
            p.sendMessage(Messages.get("TimeOut"));
        }
        // Cùng luồng rời phòng như /phoban leave: global spawn hoặc vị trí trước khi vào (PlayerData), có trừ điểm như thua.
        for (Player p : snapshot) {
            if (PlayerData.contains(p)) {
                game.leave(p, false, true, false);
            }
        }
        if (!game.getPlayers().isEmpty()) {
            game.forceStopAndReset();
        }
    }

    private void handleRespawning() {
        for (Player player : game.getPlayers()) {
            PlayerData data = PlayerData.get(player);
            if (data == null || !data.isRespawning()) continue;

            if (data.getRespawnCountdown() <= 0) {
                data.finishRespawn();
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(Messages.get("Respawn").replace("<amount>", String.valueOf(data.remainRespawn())));
                if (getConfig().getBoolean("Settings.Checkpoint") && data.hasDeath()) {
                    player.teleport(data.getLastDeath());
                } else {
                    player.teleport(game.mobLocation(0));
                }
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                String title = Messages.get("Respawning.Title").replace("<time>", String.valueOf(data.getRespawnCountdown()));
                String subtitle = Messages.get("Respawning.Subtitle").replace("<time>", String.valueOf(data.getRespawnCountdown()));
                player.sendTitle(title, subtitle, 0, 25, 5);
                data.tickRespawnCountdown();
            }
        }
    }

    private void handleStageCountdown() {
        sendCountdown("StageCountdown");
        countdown--;
        if (countdown > -1) {
            return;
        }

        int result = advanceSkippingEmptyStages();
        countdown = result == 1
                ? getConfig().getInt("Settings.QuitCountdown")
                : getConfig().getInt("Settings.StageCountdown");
    }

    private int advanceSkippingEmptyStages() {
        game.nextStage();
        int result = game.newStage();
        int attempts = 3;
        while (result == 2 && attempts-- > 0) {
            game.nextStage();
            result = game.newStage();
        }
        return result;
    }

    private void handleQuitCountdown() {
        if (Messages.has("QuitCountdown." + countdown)) {
            String msg = Messages.get("QuitCountdown." + countdown);
            game.getPlayers().forEach(p -> p.sendMessage(msg));
        }
        countdown--;
        if (countdown <= 0) {
            game.leaveAllAfterComplete();
            countdown = getConfig().getInt("Settings.StartCountdown");
        }
    }

    private void sendCountdown(String prefix) {
        if (!Messages.has(prefix + "." + countdown)) return;
        String title = Messages.get(prefix + "." + countdown + ".Title");
        String subtitle = Messages.get(prefix + "." + countdown + ".Subtitle");
        for (Player p : game.getPlayers()) Utils.sendTitle(p, title, subtitle);
    }

    private void sendTimeRemaining() {
        int t = game.getTimeLeft();
        if (t <= 1) {
            return;
        }
        if (!Messages.has("TimeRemaining." + t)) {
            return;
        }
        String msg = Messages.get("TimeRemaining." + t);
        game.getPlayers().forEach(p -> p.sendMessage(msg));
    }

    private void sendWaitingStartWarning() {
        if (!getConfig().getBoolean("Settings.WaitingStartWarning.Enable", true)) return;
        int interval = Math.max(1, getConfig().getInt("Settings.WaitingStartWarning.Interval", 5));
        if (waitingKick % interval != 0) return;

        String title = getConfig().getString("Settings.WaitingStartWarning.Title",
                "<#FF5555>&lPHÒNG CHỜ");
        String subtitle = getConfig().getString("Settings.WaitingStartWarning.Subtitle",
                "<#FFFFFF>Gõ <#00FF88>/phoban start <#FFFFFF>để bắt đầu");
        boolean leaderOnly = getConfig().getBoolean("Settings.WaitingStartWarning.LeaderOnly", true);

        for (Player player : game.getPlayers()) {
            if (leaderOnly && !game.isLeader(player)) continue;
            Utils.sendTitle(player, title, subtitle);
        }
    }

    public void setCountdown(int countdown) { this.countdown = countdown; }

    private FileConfiguration getConfig() {
        return FileManager.getFileConfig(FileManager.Files.CONFIG);
    }

    private void logWaiting(String message) {
        DebugLogger.logWaiting(getConfig().getBoolean("Settings.DebugWaitingRoom", false),
                "room=" + game.getName() + " status=" + game.getStatus() + " :: " + message);
    }
}
