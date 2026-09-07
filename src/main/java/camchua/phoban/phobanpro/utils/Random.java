package camchua.phoban.phobanpro.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Random {

    private final List<Chance> chances = new ArrayList<>();
    private double sum = 0;

    public void addChance(Object element, double chance) {
        chances.add(new Chance(element, sum, sum + chance));
        sum += chance;
    }

    public void removeChance(Object element) {
        Iterator<Chance> it = chances.iterator();
        double removedWeight = 0;
        boolean found = false;
        while (it.hasNext()) {
            Chance c = it.next();
            if (found) {
                c.lowerLimit -= removedWeight;
                c.upperLimit -= removedWeight;
            }
            if (!found && c.element.equals(element)) {
                removedWeight = c.upperLimit - c.lowerLimit;
                sum -= removedWeight;
                it.remove();
                found = true;
            }
        }
    }

    public Object getRandomElement() {
        if (chances.isEmpty() || sum <= 0) return null;
        for (int attempt = 0; attempt < 5; attempt++) {
            double idx = ThreadLocalRandom.current().nextDouble(0, sum);
            for (Chance c : chances) {
                if (c.lowerLimit <= idx && c.upperLimit > idx) return c.element;
            }
        }
        return chances.get(0).element;
    }

    public int getChoices() { return chances.size(); }

    private static class Chance {
        double lowerLimit, upperLimit;
        final Object element;

        Chance(Object element, double lower, double upper) {
            this.element = element;
            this.lowerLimit = lower;
            this.upperLimit = upper;
        }
    }
}
