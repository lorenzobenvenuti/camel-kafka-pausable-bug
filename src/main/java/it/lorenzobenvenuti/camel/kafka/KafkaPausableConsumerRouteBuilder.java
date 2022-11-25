package it.lorenzobenvenuti.camel.kafka;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KafkaPausableConsumerRouteBuilder extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPausableConsumerRouteBuilder.class);

    private ScheduledExecutorService executorService;

    private static final int SIMULATED_FAILURES = 5;
    private LongAdder count = new LongAdder();

    private boolean canContinue() {
        // First one should go through ...
        if (count.intValue() <= 1) {
            LOG.info("Count is 1, allowing processing to proceed");
            return true;
        }

        if (count.intValue() >= SIMULATED_FAILURES) {
            LOG.info("Count is {}, allowing processing to proceed because it's greater than retry count {}",
                    count.intValue(), SIMULATED_FAILURES);
            return true;
        }

        LOG.info("Cannot proceed at the moment ... count is {}", count.intValue());
        return false;
    }

    public void increment() {
        count.increment();
    }

    public int getCount() {
        return count.intValue();
    }

    public void reset() {
        count.reset();
    }

    @Override
    public void configure() throws Exception {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("pausable");

        /*
         * Set a watcher for the circuit breaker events. This watcher simulates a check for a downstream
         * system availability. It watches for error events and, when they happen, it triggers a scheduled
         * check (in this case, that simply increments a value). On success, it shuts down the scheduled check
         */
        circuitBreaker.getEventPublisher()
                .onSuccess(event -> {
                    LOG.info("Downstream call succeeded");
                    if (executorService != null) {
                        executorService.shutdownNow();
                        executorService = null;
                    }
                })
                .onError(event -> {
                    LOG.info(
                            "Downstream call error. Starting a thread to simulate checking for the downstream availability");

                    if (executorService == null) {
                        executorService = Executors.newSingleThreadScheduledExecutor();
                        executorService.scheduleAtFixedRate(() -> increment(), 1, 1, TimeUnit.SECONDS);
                    }
                });

        // Binds the configuration to the registry
        getCamelContext().getRegistry().bind("pausableCircuit", circuitBreaker);

        from("kafka:my-topic?groupId=my-consumer-group")
                .pausable(new KafkaConsumerListener(), o -> canContinue())
                .routeId("pausable-it")
                .process(exchange -> LOG.info("Got record from Kafka: {}", exchange.getMessage().getBody()))
                .circuitBreaker()
                .resilience4jConfiguration().circuitBreaker("pausableCircuit").end()
                .to("direct:intermediate");

        from("direct:intermediate")
                .process(exchange -> {
                    LOG.info("Got record on the intermediate processor: {}", exchange.getMessage().getBody());

                    if (getCount() < SIMULATED_FAILURES) {
                        throw new RuntimeCamelException("Error");
                    }
                })
                .log("Message sucessfully processed ${body}")
                .end();
    }

}
