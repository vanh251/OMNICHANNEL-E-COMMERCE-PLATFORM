# Architecture Reference

## 1. System Overview

The system is an Omnichannel E-Commerce Platform implemented using
Microservices Architecture.

Main components:

- API Gateway
- Identity & User Service
- Product Catalog Service
- Order & Inventory Service
- Payment & Billing Service
- Notification Service

The architecture separates business capabilities so that each service
can be developed, deployed, and scaled independently.

---

# 2. High-Level Architecture

```text
                    WEB / MOBILE CLIENT
                           |
                         HTTPS
                           |
                           v
                    +-------------+
                    | API GATEWAY |
                    +-------------+
                           |
          +----------------+----------------+
          |                |                |
          v                v                v
     Identity          Product          Order &
      Service          Service         Inventory
          |                |                |
       PostgreSQL       MongoDB          PostgreSQL
                                            |
                                           Redis

                           |
                           v
                    Payment Service
                           |
                       PostgreSQL

                           |
                           v
                    RabbitMQ / Kafka
                           |
              +------------+------------+
              |                         |
              v                         v
       Notification              Order Service
          Service                Event Consumer