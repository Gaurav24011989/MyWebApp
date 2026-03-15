package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HelloController.class)
public class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void helloEndpointReturnsGreeting() throws Exception {
        String name = "World";
        mockMvc.perform(get("/hello/{name}", name))
                .andExpect(status().isOk())
                .andExpect(content().string("hello " + name));
    }

    @Test
    public void helloEndpointWithAnotherName() throws Exception {
        String name = "Gemini";
        mockMvc.perform(get("/hello/{name}", name))
                .andExpect(status().isOk())
                .andExpect(content().string("hello " + name));
    }
}