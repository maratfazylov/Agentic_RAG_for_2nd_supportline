package com.hybridrag.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "expert")
public class ExpertProperties {

    private Activity activity = new Activity();
    private Routing routing = new Routing();

    public Activity getActivity() { return activity; }
    public void setActivity(Activity activity) { this.activity = activity; }

    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }

    public static class Activity {
        private int threshold = 5;
        private int windowDays = 30;

        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }

        public int getWindowDays() { return windowDays; }
        public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
    }

    public static class Routing {
        private double threshold = 0.6;

        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
    }
}
