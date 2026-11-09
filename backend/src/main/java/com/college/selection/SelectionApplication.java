package com.college.selection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 学生实训室进组选拔系统 · 后端入口。
 *
 * 分层约定（详见 backend/README.md）：
 *   controller  只做参数校验与返回包装，不写业务规则
 *   service     选拔规则、状态流转、并发控制
 *   mapper      MyBatis 接口，SQL 放 resources/mapper/*.xml
 *   entity      与表一一对应
 *   dto         请求/响应对象，别直接把 entity 返回给前端
 */
@SpringBootApplication
@EnableTransactionManagement
public class SelectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SelectionApplication.class, args);
    }
}
