package com.chronobeat.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class DefaultRandomProvider implements RandomProvider {

    @Override
    public int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    @Override
    public <T> T pick(List<T> items) {
        return items.get(nextInt(items.size()));
    }
}
