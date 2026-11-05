# Task runners are instantiated from a persisted class name by TaskRunnerFactory.
-keep public class * extends com.marianhello.bgloc.headless.AbstractTaskRunner {
    public <init>();
}
