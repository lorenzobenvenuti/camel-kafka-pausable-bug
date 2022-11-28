# camel-kafka-pausable-bug

This project runs the Camel route used in [this test](https://github.com/apache/camel/blob/2c01082032346753cc6621ab4530b0969f12bac7/components/camel-kafka/src/test/java/org/apache/camel/component/kafka/integration/pause/KafkaPausableConsumerCircuitBreakerIT.java)
and shows that when the consumer is resumed then all the messages (starting from the earliest offset) are consumed.

## How to reproduce the issue

* Run Kafka: `docker-compose -f docker-compose.yaml up`
* Run the application: `mvn spring-boot:run`
* Send a batch of messages: `for i in $(seq 1 5); do ./send-message.sh  "Message $i"; done`
* Reset the counter: `curl -X POST http://localhost:8080/reset` (to simulate another downstream "outage")
* Send another batch of messages: `for i in $(seq 6 10); do ./send-message.sh  "Message $i"; done`

**Expected result**: each message is consumed once

**Actual result**: the second time the consumer is resumed, it reads the messages since the beginning:
messages 1-5 are consumed twice.
   
## Notes

* The test mentioned above is setting `autoOffsetReset=earliest`; it looks like the issue happens
  regardless of the setting
* As far as I can tell the issue happens because `KafkaConsumerListener#afterProcess` invokes
  `consumer.seekToBeginning`
