package com.jayant.payment.SentinelLedger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SentinelLedgerApplicationTests {

	@Test
	void contextLoads() {
	}

}
