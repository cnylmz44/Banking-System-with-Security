# Banking Project
### Technologies
- _Java_
- _Spring Boot_
- _MyBatis_
- _JDBC_
- _Apache Kafka_
- _Spring Security_
- _JWT_
- _MySQL_
- _Collect API_

### Entities
- Bank
_{
int id PRIMARY KEY AUTO_INCREMENT,
string name NOT NULL UNIQUE
}_

- Bank User
_{
int id PRIMARY KEY AUTO_INCREMENT,
String username NOT NULL UNIQUE,
String email NOT NULL UNIQUE,
String password NOT NULL,
boolean enabled DEFAULT true,
String authorities
}_

- Bank Account
_{
int id PRIMARY KEY AUTO_INCREMENT,
user_id FOREING KEY(users.id),
bank_id FOREIGN_KEY(banks.id),
number int(10),
enum type(TL,ALTIN,DOLAR)(String'de tutlabilir size bırakıyorum),
double balance DEFAULT 0,
timestamp creation_date,
timestamp last_update_date,
boolean is_deleted DEFAULT false
}_
