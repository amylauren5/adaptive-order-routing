package ict.um.pharmaceutical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class PharmaConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PharmaConsumerApplication.class, args);
    }
}
