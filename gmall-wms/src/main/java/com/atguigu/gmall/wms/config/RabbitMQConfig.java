package com.atguigu.gmall.wms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Bean("WMS-TTL-QUEUE")
    public Queue ttlQueue(){
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", "GMALL-ORDER-EXCHANGE");
        arguments.put("x-dead-letter-routing-key", "stock.unlock");
        arguments.put("x-message-ttl", 120000);
        return new Queue("WMS-TTL-QUEUE", true, false, false, arguments);
    }

    @Bean("WMS-TTL-BINDING")
    public Binding ttlbinding(){
        return new Binding("WMS-TTL-QUEUE",Binding.DestinationType.QUEUE,"GMALL-ORDER-EXCHANGE","stock.ttl",null);
    }

}
