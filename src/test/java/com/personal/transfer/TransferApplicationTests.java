package com.personal.transfer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "aws.sqs.consumer.enabled=false")
class TransferApplicationTests {

	@Test
	void contextLoads() {
	}
}
