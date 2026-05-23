package com.exammarker.helloworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloworldApplication {


	public static void main(String[] args) {
		SpringApplication.run(HelloworldApplication.class, args);
	}
	
//	@Bean
//    CommandLineRunner runner(OpenAiChatModel chatModel) {
//        return args -> {
//            System.out.println("--- Sending request to OpenAI ---");
//            
//            // This is the "Hello World" call
//            String response = chatModel.call("Hello Michael! Say 'Hello World' back to me. And please tell me my name.. hahaha.. mention a song if it comes to you");
//            
//            System.out.println("OpenAI says: " + response);
//        };
//    }

//	@Bean
//	CommandLineRunner gradeExam() {
//
//		return args -> {
//
//			String studentWork = "/Users/hammadquddus/Downloads/upload-unmarked-papers.pdf";
//
//			String solutions = "/Users/hammadquddus/Downloads/upload-exam-solutions.pdf";
//
//			String rubric = "/Users/hammadquddus/Downloads/upload-level-descriptor.pdf";
//
//			service.evaluate(studentWork, rubric, solutions);
//
//		};
//	}

}
