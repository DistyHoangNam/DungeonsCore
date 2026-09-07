package camchua.phoban.phobanpro.game;

public class GameMob {

    private final String key;
    private final String type;
    private final int amount;

    public GameMob(String key, String type, int amount) {
        this.key = key;
        this.type = type;
        this.amount = amount;
    }

    public String getKey() { return key; }
    public String getType() { return type; }
    public int getAmount() { return amount; }
}
