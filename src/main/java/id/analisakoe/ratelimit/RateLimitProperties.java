package id.analisakoe.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private List<Endpoint> endpoints = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<Endpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<Endpoint> endpoints) { this.endpoints = endpoints; }

    public static class Endpoint {
        private String pattern;
        private long capacity = 10;
        private long refillTokens = 10;
        private long refillMinutes = 1;
        private String keyType = "IP";   // IP | USER

        public String getPattern() { return pattern; }
        public void setPattern(String v) { this.pattern = v; }
        public long getCapacity() { return capacity; }
        public void setCapacity(long v) { this.capacity = v; }
        public long getRefillTokens() { return refillTokens; }
        public void setRefillTokens(long v) { this.refillTokens = v; }
        public long getRefillMinutes() { return refillMinutes; }
        public void setRefillMinutes(long v) { this.refillMinutes = v; }
        public String getKeyType() { return keyType; }
        public void setKeyType(String v) { this.keyType = v; }
    }
}