package it.lorenzobenvenuti.camel.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResetCounterController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetCounterController.class);

    @Autowired
    KafkaPausableConsumerRouteBuilder routeBuilder;

    @PostMapping("/reset")
    public void reset() {
        LOGGER.info("Resetting counter");
        routeBuilder.reset();
    }

}
