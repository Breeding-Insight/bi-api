package org.breedinginsight.utilities.cache;

import io.micronaut.context.annotation.Value;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class RedissonManager implements ApplicationEventListener<ServerShutdownEvent> {
    private final RedissonClient redissonClient;

    public RedissonManager(@Value("${redisson.single-server-config}") String configFile) throws IOException {
        Config config = Config.fromYAML(RedissonManager.class.getResourceAsStream(configFile));
        this.redissonClient = Redisson.create(config);
    }

    @Override
    public void onApplicationEvent(ServerShutdownEvent event) {
        redissonClient.shutdown();
    }
}
