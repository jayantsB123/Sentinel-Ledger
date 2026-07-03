package com.jayant.payment.SentinelLedger;

import com.jayant.payment.Sentinel_Ledger.SentinelLedgerApplication;
import org.springframework.boot.SpringApplication;

public class TestSentinelLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(SentinelLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
