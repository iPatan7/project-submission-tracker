package edu.cu.tracker;

import com.google.gson.Gson;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

import java.util.List;
import java.util.Optional;

/**
 * Keeps every submission as a JSON string under "tracker:submission:<id>".
 * "tracker:seq" gives the next id and the list "tracker:ids" keeps the order.
 */
public class RedisStore implements SubmissionStore {

    private static final String SEQ = "tracker:seq";
    private static final String IDS = "tracker:ids";

    private final JedisPooled redis;
    private final Gson gson = new Gson();

    public RedisStore(String host, int port, String password) {
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .password(password)
                .timeoutMillis(2000)
                .build();
        this.redis = new JedisPooled(new HostAndPort(host, port), config);
    }

    @Override
    public Submission save(Submission submission) {
        long id = redis.incr(SEQ);
        Submission saved = submission.withId(id);
        redis.set(key(id), gson.toJson(saved));
        redis.rpush(IDS, String.valueOf(id));
        return saved;
    }

    @Override
    public Optional<Submission> find(long id) {
        String json = redis.get(key(id));
        return Optional.ofNullable(json).map(j -> gson.fromJson(j, Submission.class));
    }

    @Override
    public List<Submission> findAll() {
        return redis.lrange(IDS, 0, -1).stream()
                .map(id -> find(Long.parseLong(id)))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void update(Submission submission) {
        redis.set(key(submission.id()), gson.toJson(submission));
    }

    @Override
    public void ping() {
        redis.ping();
    }

    @Override
    public String name() {
        return "redis";
    }

    private static String key(long id) {
        return "tracker:submission:" + id;
    }
}
