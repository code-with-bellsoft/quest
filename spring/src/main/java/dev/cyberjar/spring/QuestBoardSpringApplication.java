package dev.cyberjar.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class QuestBoardSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestBoardSpringApplication.class, args);
    }

}
