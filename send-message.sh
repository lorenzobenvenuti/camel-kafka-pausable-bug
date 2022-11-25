#!/bin/bash

docker exec -it kafka sh -c "echo $1 | /opt/bitnami/kafka/bin/kafka-console-producer.sh --topic my-topic --broker-list localhost:9092"
