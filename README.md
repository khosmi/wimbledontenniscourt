# wimbledon tennis court resevation

![image](https://user-images.githubusercontent.com/86760622/132145838-4d3cf2f6-bca4-4a99-bb7a-4f96510359b1.png)


# 서비스 시나리오
### 기능적 요구사항
1. wimbledon 테니스대회에 참가한 선수들이 연습을 위해 테니스 코트를 예약/승인하는 시스템이다.
2. 참가선수들은 사용할 테니스 코르를 예약한다
3. 테니스 코드 관리자는 예약을 승인한다
5. 참가선수들은 나의 코트 예약현황에서 예약현황 및 상태를 조회할 수 있다.
6. 참가선수들은 예약을 취소 할 수 있다.
7. 참가선수들이 예약을 취소하면 코트사용 승인정보가 취소되어야 한다.

### 비기능적 요구사항
1. 트랜젝션
   1. 예약 취소시 코트사용 승인정보가 반드시 등록되어야 한다.  → REQ/RES Sync 호출
2. 장애격리
   1. 승인시스템에서 장애가 발송해도 예약은 가능해야 한다 →Async(event-driven), Eventual Consistency
   1. 승인취소가 과중되면 예약을 잠시 후에 하도록 유도한다 → Circuit breaker, fallback
3. 성능
   1. 참가선수들이 코트예약상태를 확인할 수 있어야 한다 → CQRS


# Event Storming 결과
![image](https://user-images.githubusercontent.com/86760622/130416307-f2fc6258-6512-4a41-bb9e-787cb997ceae.png)


# 헥사고날 아키텍처 다이어그램 도출
![image](https://user-images.githubusercontent.com/86760613/131060623-ad62a938-b703-43d6-b23e-f6f6a317e942.png)

# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다. (각각의 포트넘버는 8080 ~ 8084이다)
```
cd gateway
mvn spring-boot:run

cd Reservation
mvn spring-boot:run

cd Pay
mvn spring-boot:run

cd Ticket
mvn spring-boot:run

cd MyReservation
mvn spring-boot:run
```

## DDD 의 적용
msaez.io를 통해 구현한 Aggregate 단위로 Entity를 선언 후, 구현을 진행하였다.
Entity Pattern과 Repository Pattern을 적용하기 위해 Spring Data REST의 RestRepository를 적용하였다.

**Reservation 서비스의 Reservation.java**
```java 
package movie;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Reservation_table")
public class Reservation {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String userid;
    private String movie;
    private String theater;
    private String time;
    private String seatNo;
    private Integer price;
    private String cardNo;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Reserved reserved = new Reserved();
        BeanUtils.copyProperties(this, reserved);
        reserved.setStatus("Reserved");  // 예약상태 입력 by khos
        reserved.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        movie.external.Pay pay = new movie.external.Pay();
        // mappings goes here
        BeanUtils.copyProperties(this, pay); // Pay 값 설정 by khos
        pay.setReservationId(reserved.getId());
        pay.setStatus("reserved"); // Pay 값 설정 by khos
        ReservationApplication.applicationContext.getBean(movie.external.PayService.class)
            .pay(pay);

    }
    @PreRemove
    public void onPreRemove(){
        CanceledReservation canceledReservation = new CanceledReservation();
        BeanUtils.copyProperties(this, canceledReservation);
        canceledReservation.setStatus("Canceled Reservation");  // 예약상태 입력 by khos
        canceledReservation.publishAfterCommit();

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }
    public String getMovie() {
        return movie;
    }

    public void setMovie(String movie) {
        this.movie = movie;
    }
    public String getTheater() {
        return theater;
    }

    public void setTheater(String theater) {
        this.theater = theater;
    }
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}

```

**Pay 서비스의 PolicyHandler.java**
```java
package movie;

import movie.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class PolicyHandler{
    @Autowired PayRepository payRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCanceledReservation_CancelPay(@Payload CanceledReservation canceledReservation){

         try {
            if (!canceledReservation.validate()) return;
                // view 객체 조회

                    List<Pay> payList = payRepository.findByReservationId(canceledReservation.getId());
                    for(Pay pay : payList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    pay.setStatus(canceledReservation.getStatus());
                // view 레파지 토리에 save
                payRepository.save(pay);
                }

        }catch (Exception e){
            e.printStackTrace();
        }

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}

}

```


**Pay 서비스의 Pay.java**
```java
package movie;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Pay_table")
public class Pay {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long reservationId;
    private String userid;
    private String movie;
    private String theater;
    private String time;
    private Integer price;
    private String cardNo;
    private String status;
    private String seatNo;

    @PostPersist
    public void onPostPersist(){
        Payed payed = new Payed();
        BeanUtils.copyProperties(this, payed);
        payed.publishAfterCommit();

    }

    @PostUpdate
    public void onPostUpdate(){
        Payed payed = new Payed();
        BeanUtils.copyProperties(this, payed);
        payed.publishAfterCommit();
    }

    @PreRemove
    public void onPreRemove(){
        CanceledPay canceledPay = new CanceledPay();
        BeanUtils.copyProperties(this, canceledPay);
        canceledPay.setStatus("Canceled Payment");  // 상태 변경 by khos
        canceledPay.publishAfterCommit();

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }
    public String getMovie() {
        return movie;
    }

    public void setMovie(String movie) {
        this.movie = movie;
    }
    public String getTheater() {
        return theater;
    }

    public void setTheater(String theater) {
        this.theater = theater;
    }
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }


}

```
**Ticket 서비스의 PolicyHandler.java**
```java
package movie;

import movie.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;


@Service
public class PolicyHandler{
    @Autowired TicketRepository ticketRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserved_Ticket(@Payload Reserved reserved){

        if(!reserved.validate()) return;

        System.out.println("\n\n##### listener Ticket : " + reserved.toJson() + "\n\n");


        // Sample Logic // ticket 데이터 저장 
        Ticket ticket = new Ticket();
        ticket.setMovie(reserved.getMovie());
        //ticket.setPayId(reserved.getId());
        ticket.setReservationId(reserved.getId());
        ticket.setSeatNo(reserved.getSeatNo());
        ticket.setStatus(reserved.getStatus());
        ticket.setTheater(reserved.getTheater());
        ticket.setTime(reserved.getTime());
        ticket.setUserid(reserved.getUserid());
        ticketRepository.save(ticket);

        // ticket 데이터 저장 
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenPayed__Ticket(@Payload Payed payed) {
        try {
            if (!payed.validate()) return;
                // view 객체 조회

                    List<Ticket> ticketList = ticketRepository.findByReservationId(payed.getReservationId());
                    for(Ticket ticket : ticketList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    ticket.setPayId(payed.getId());
                    ticket.setStatus(payed.getStatus());
                // view 레파지 토리에 save
                ticketRepository.save(ticket);
                }

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCanceledPay_CancelTicket(@Payload CanceledReservation canceledReservation){
      
        try {
            if (!canceledReservation.validate()) return;
                // view 객체 조회

                    List<Ticket> ticketList = ticketRepository.findByReservationId(canceledReservation.getId());
                    for(Ticket ticket : ticketList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    ticket.setStatus(canceledReservation.getStatus());
                // view 레파지 토리에 save
                ticketRepository.save(ticket);
                }

        }catch (Exception e){
            e.printStackTrace();
        }
        
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}


```



**Ticket 서비스의 Ticket.java**
```java
package movie;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Ticket_table")
public class Ticket {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long reservationId;
    private Long payId;
    private String userid;
    private String movie;
    private String theater;
    private String time;
    private String seatNo;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Ticketed ticketed = new Ticketed();
        BeanUtils.copyProperties(this, ticketed);
        ticketed.publishAfterCommit();

    }

    @PostUpdate
    public void onPostUpdate(){
        Ticketed ticketed = new Ticketed();
        BeanUtils.copyProperties(this, ticketed);
        ticketed.publishAfterCommit();

    }

    @PreRemove
    public void onPreRemove(){
        CanceledTicket canceledTicket = new CanceledTicket();
        BeanUtils.copyProperties(this, canceledTicket);
        canceledTicket.publishAfterCommit();

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
    public Long getPayId() {
        return payId;
    }

    public void setPayId(Long payId) {
        this.payId = payId;
    }
    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }
    public String getMovie() {
        return movie;
    }

    public void setMovie(String movie) {
        this.movie = movie;
    }
    public String getTheater() {
        return theater;
    }

    public void setTheater(String theater) {
        this.theater = theater;
    }
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}

```

DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.

- Resevation 서비스 호출 결과 

![image](https://user-images.githubusercontent.com/86760622/130421675-11836da1-dbe8-48b5-a241-90a1855b7a96.png)

- Pay 서비스 호출 결과 

![image](https://user-images.githubusercontent.com/86760622/130421919-df745446-0c4d-42f6-9792-fcb399062966.png)

- Ticket 서비스 호출 결과

![image](https://user-images.githubusercontent.com/86760622/130422013-a3e30485-5869-4716-84fe-a3a3b49c3277.png)

- MyReservation 서비스 호출 결과 

![image](https://user-images.githubusercontent.com/86760622/130422106-b95d5fcf-92c8-438e-abdd-27250e32464c.png)




# GateWay 적용
API GateWay를 통하여 마이크로 서비스들의 집입점을 통일할 수 있다. 다음과 같이 GateWay를 적용하였다.

```yaml
server:
  port: 8080

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: Reservation
          uri: http://localhost:8081
          predicates:
            - Path=/reservations/** 
        - id: Pay
          uri: http://localhost:8082
          predicates:
            - Path=/pays/** 
        - id: Ticket
          uri: http://localhost:8083
          predicates:
            - Path=/tickets/** 
        - id: MyReservation
          uri: http://localhost:8084
          predicates:
            - Path= /myReservations/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true
```
8080 port로 Reservation 서비스 정상 호출

![image](https://user-images.githubusercontent.com/86760622/130422248-3f5dc3f6-7073-4b18-8ae5-50429dd94ab2.png)



# CQRS/saga/correlation
Materialized View를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이)도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다. 
본 프로젝트에서 View 역할은 MyReservation 서비스가 수행한다.

예약 실행 후 Pay, Ticket, MyReservation 화면 - reserved 상태로 예약정보 등록

![image](https://user-images.githubusercontent.com/86760622/131072020-92613585-39b2-423f-abc9-69368fa82eed.png)

![image](https://user-images.githubusercontent.com/86760622/131072063-a30f0933-8cc4-4526-8457-7772ec7da37e.png)

![image](https://user-images.githubusercontent.com/86760622/131072093-75d058e9-6e2f-4e66-a183-734ecbe0b420.png)

![image](https://user-images.githubusercontent.com/86760622/131072108-27b77b3c-9a03-4236-804e-a153e3837a44.png)

![image](https://user-images.githubusercontent.com/86760622/131072127-7c77461c-f778-4006-851b-ab7e6cd08c61.png)


결제 후 Ticket, MyReservation 화면 - payed 상태로 변경

![image](https://user-images.githubusercontent.com/86760622/131072212-705a10a2-c3e6-4f6a-9786-de2f4c83cc20.png)

![image](https://user-images.githubusercontent.com/86760622/131072274-f5781b82-35e8-44af-8ec8-5b317cd88fc2.png)

![image](https://user-images.githubusercontent.com/86760622/131072294-a034f344-587f-41e9-b56c-939804afd232.png)


티켓팅 후 MyReservation 화면

![image](https://user-images.githubusercontent.com/86760622/131072360-a72a3598-18a9-47c4-9176-415cda9ef812.png)

![image](https://user-images.githubusercontent.com/86760622/131072373-6df6b6d4-7d59-4533-a199-39f697fa1c17.png)


예약취소 후 Pay, Ticket, MyReservation 화면 - 예약은 삭제되며 각 서비스의 상태가 Canceled Reservation 상태로 변경됨

![image](https://user-images.githubusercontent.com/86760622/131072504-23f52839-b8fc-446b-8769-0bc2be8bf525.png)

![image](https://user-images.githubusercontent.com/86760622/131072520-545e9b28-a3e5-4150-a5f1-08fc31d32425.png)

![image](https://user-images.githubusercontent.com/86760622/131072538-a2888d11-54bc-4d5b-8f87-1cbde344f348.png)

![image](https://user-images.githubusercontent.com/86760622/131072572-b57b3ee6-f198-489f-af8c-c61cd5f3941a.png)


위와 같이 예약을 하게되면 Reservation > Pay > Ticket > MyReservation로 예약이 Assigned 되고

예약 취소가 되면 Status가 Cancelled Reservation로 Update 되는 것을 볼 수 있다.

또한 Correlation을 Key를 활용하여 Id를 Key값을 하고 원하는 예약하고 서비스간의 공유가 이루어 졌다.

위 결과로 서로 다른 마이크로 서비스 간에 트랜잭션이 묶여 있음을 알 수 있다.

# 폴리글랏
Reservation 서비스의 DB와 MyReservation의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.

**Reservation의 pom.xml DB 설정 코드**

![image](https://user-images.githubusercontent.com/86760622/131057448-457e2423-f202-4582-b820-65c4d21e4b68.png)

**MyReservation의 pom.xml DB 설정 코드**

![image](https://user-images.githubusercontent.com/86760622/131057400-b019383d-5444-4256-8f8f-9002d5eca14f.png)


# 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 예약(Reservation)와 결제(Pay)간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하도록 한다.

**Reservation 서비스 내 external.PayService.java**
```java
package movie.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="Pay", url="${api.url.pay}")  // Pay Service URL 변수화 
public interface PayService {
    @RequestMapping(method= RequestMethod.GET, path="/pays")
    public void pay(@RequestBody Pay pay);

}

```

**동작 확인**

Pay 서비스 중지함
![image](https://user-images.githubusercontent.com/86760622/131061678-fec8d91c-e3a8-413b-960b-9f904c5f604c.png)


예약시 Pay서비스 중지로 인해 예약 실패
![image](https://user-images.githubusercontent.com/86760622/131061604-77f5654c-23e4-4414-9224-d9e439ae3a32.png)


Pay 서비스 재기동 후 예약 성공함
![image](https://user-images.githubusercontent.com/86760622/131062000-cdcbb6b1-790c-4809-9ba9-d995202b45ff.png)


Pay 서비스 조회시 정상적으로 예약정보가 등록됨

![image](https://user-images.githubusercontent.com/86760622/131062120-8f310731-85b6-46c0-bdd6-caa6a22e2b09.png)

Fallback 설정 
- external.PayService.java
```java

package movie.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

//@FeignClient(name="Pay", url="${api.url.pay}")  // Pay Service URL 변수화 
@FeignClient(name="Pay", url="${api.url.pay}", fallback=PayServiceImpl.class)  // FALLBAK 설정
public interface PayService {
    @RequestMapping(method= RequestMethod.GET, path="/pays")
    public void pay(@RequestBody Pay pay);

}

```
- external.PayServiceImpl.java
```java
package movie.external;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class PayServiceImpl implements PayService {
    
    public void pay(Pay pay) {
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");
        System.out.println("@@@@@@@결제 서비스 지연중 입니다. @@@@@@@@@@@@");

    }

}


```

Fallback 결과(Pay service 종료 후 예약실행 추가 시)
![image](https://user-images.githubusercontent.com/86760622/131062766-99148589-21f6-4817-8fdd-331620f49e40.png)

# 운영

## CI/CD
* 카프카 설치
```
- 헬름 설치
참고 : http://msaschool.io/operation/implementation/implementation-seven/
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 > get_helm.sh
chmod 700 get_helm.sh
./get_helm.sh

- Azure Only
kubectl patch storageclass managed -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

- 카프카 설치
kubectl --namespace kube-system create sa tiller      # helm 의 설치관리자를 위한 시스템 사용자 생성
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller

helm repo add incubator https://charts.helm.sh/incubator
helm repo update
kubectl create ns kafka
helm install my-kafka --namespace kafka incubator/kafka

kubectl get po -n kafka -o wide
```
* Topic 생성
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --topic movie --create --partitions 1 --replication-factor 1
```
* Topic 확인
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --list
```
* 이벤트 발행하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-producer --broker-list my-kafka:9092 --topic movie
```
* 이벤트 수신하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-consumer --bootstrap-server my-kafka:9092 --topic movie
```

* 소스 가져오기
```
git clone https://github.com/khosmi/movie.git
```

## Deploy / Pipeline

* Azure 레지스트리에 도커 이미지 push, deploy, 서비스생성(yml파일 이용한 deploy)
```
# 각 마이크로 서비스의 deployment에서 이미지 수정 필요
# label과 이미지 이름 소문자로 변경 필요


cd Pay
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user1919.azurecr.io/pay .
# acr에 이미지 푸시
docker push user1919.azurecr.io/pay
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포 
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment pay  
cd ..

cd Reservation
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user1919.azurecr.io/reservation .
# acr에 이미지 푸시
docker push user1919.azurecr.io/reservation
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포 
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment reservation  
cd ..

cd Ticket
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user1919.azurecr.io/ticket .
# acr에 이미지 푸시
docker push user1919.azurecr.io/ticket
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment ticket  
cd ..

cd gateway
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user1919.azurecr.io/gateway .
# acr에 이미지 푸시
docker push user1919.azurecr.io/gateway
# kubernetes에 service, deployment 배포
kubectl create deploy gateway --image=user1919.azurecr.io/gateway   
kubectl expose deploy gateway --type=LoadBalancer --port=8080 

kubectl rollout restart deployment gateway
cd ..

cd MyReservation
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user1919.azurecr.io/myreservation .
# acr에 이미지 푸시
docker push user1919.azurecr.io/myreservation
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment myreservation  
cd ..

```
* Service, Pod, Deploy 상태 확인

![image](https://user-images.githubusercontent.com/86760528/131059867-8d387dc1-bac2-4d68-972b-1cc1d0629d78.png)


* deployment.yml  참고

![image](https://user-images.githubusercontent.com/86760528/131059850-1c47652c-72d2-413b-9e6d-3733d519c1e5.png)

## 서킷 브레이킹
* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함
* Reservation -> Pay 와의 Req/Res 연결에서 요청이 과도한 경우 CirCuit Breaker 통한 격리
* Hystrix 를 설정: 요청처리 쓰레드에서 처리시간이 1500 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정

```
// Reservation 서비스 application.yml

feign:
  hystrix:
    enabled: true

hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 1500
```


```
// Pay 서비스 Pay.java

    @PostPersist
    public void onPostPersist(){
       
        Payed payed = new Payed();
        BeanUtils.copyProperties(this, payed);
        payed.publishAfterCommit();

        try {
            Thread.currentThread().sleep((long) (1000 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
```

* siege.yaml
```
apiVersion: v1
kind: Pod
metadata:
  name: siege
spec:
  containers:
  - name: siege
    image: apexacme/siege-nginx
```

* siege pod 생성
```
/home/project/team/forthcafe/yaml/kubectl apply -f siege.yaml
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 100명 60초 동안 실시
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c100 -t30S  -v --content-type "application/json" 'http://52.141.61.164:8080/orders POST {"movie":"ironman"}'
```
![image](https://user-images.githubusercontent.com/86760528/131079671-40199483-9c22-42fc-8fb3-0dbc8a52b183.png)
![image](https://user-images.githubusercontent.com/86760528/131079931-b61cd3fa-44ac-42ea-9624-c2fa7b32ff69.png)

## ConfigMap
* MyReservation을 실행할 때 환경변수 사용하여 활성 프로파일을 설정한다.
* Dockerfile 변경
```dockerfile
FROM openjdk:8u212-jdk-alpine
COPY target/*SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx400M","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar","--spring.profiles.active=${PROFILE}"]
```
* deployment.yml 파일에 설정
```
          env:
          - name: PROFILE
            valueFrom:
              configMapKeyRef:
                name: profile-cm
                key: profile
```
* `profile=docker`를 가지는 config map 생성
```
kubectl create configmap profile-cm --from-literal=profile=docker
```
* ConfigMap 생성 확인
```
kubectl get cm profile-cm -o yaml 
```
![configmap](https://user-images.githubusercontent.com/53825723/131068300-7691fb19-bed0-4277-b535-1e53e0fcf0a7.JPG)

* 다시 배포한다.
```
mvn package
docker build -t user1919.azurecr.io/myreservation .
docker push user1919.azurecr.io/myreservation
kubectl apply -f kubernetes
```

* pod의 로그 확인
```
kubectl logs myreservation-5fd5475c4d-9bkzd
```
![configmapapplication로그](https://user-images.githubusercontent.com/53825723/131068733-3eed09a3-0af2-422a-a77d-67c6312b0647.JPG)


* pod의 sh에서 환경변수 확인
```
kubectl exec myreservation-5fd5475c4d-9bkzd -it -- sh
```
![configmapcontainer로그](https://user-images.githubusercontent.com/53825723/131068737-668acff9-33cc-4716-af9c-23d33af33e0d.JPG)


## 오토스케일 아웃
* 앞서 서킷 브레이커(CB) 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다.

*  myReservation 서비스 deployment.yml 설정
```
        resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
```
* 스크립트를 실행하여 다시 배포해준다.

* Order 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다

```
kubectl autoscale deployment myreservation --cpu-percent=15 --min=1 --max=10
```
```
kubectl get hpa
```
![hpa적용확인](https://user-images.githubusercontent.com/53825723/131067613-81203ccb-1325-4af8-bcc3-aeea62990a70.JPG)

* siege.yaml
```
apiVersion: v1
kind: Pod
metadata:
  name: siege
spec:
  containers:
  - name: siege
    image: apexacme/siege-nginx
```

* siege pod 생성
```
kubectl apply -f siege.yaml
```


* siege를 활용해서 워크로드를 1000명, 1분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c1000 -t60S  -v http://myreservation:8080/myReservations
```

* 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```
kubectl get deploy myreservation -w
```
![hpaDelploy수변경전](https://user-images.githubusercontent.com/53825723/131067624-43570d7e-354a-43fe-871b-cc7a8604b1b7.JPG)
```
 watch kubectl get pod
```
![hpaPod수변경전](https://user-images.githubusercontent.com/53825723/131067628-d6870772-3008-4dde-80ec-2c471e29eb2d.JPG)

* 오토스케일 결과
```
kubectl get deploy myreservation -w
```
![hpaDelploy수변경후](https://user-images.githubusercontent.com/53825723/131067792-e708da59-817b-4d6c-b27f-e7b0e2b26d1a.JPG)
```
 watch kubectl get pod
```
![hpaPod수변경후](https://user-images.githubusercontent.com/53825723/131067798-ceb2bd23-69e5-4d2f-835d-c8e80fc2bfe3.JPG)


## 무정지 재배포 (Readiness Probe)
* Readiness 설정이 없는 경우(deployment에서 Readiness 설정을 제거한 후 배포한다.)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myreservation
  labels:
    app: myreservation
spec:
  replicas: 1
  selector:
    matchLabels:
      app: myreservation
  template:
    metadata:
      labels:
        app: myreservation
    spec:
      containers:
        - name: myreservation
          image: user1919.azurecr.io/myreservation:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
          resources:
            limits:
              cpu: 500m
            requests:
              cpu: 200m
          env:
          - name: PROFILE
            valueFrom:
              configMapKeyRef:
                name: profile-cm
                key: profile        
```
```
kubectl apply -n huijun -f MyReservation/kubernetes/deployment.yml
```
* siege로 부하테스트를 한다. (워크로드 1000명, 1분)
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c1000 -t60S  -v http://myreservation:8080/myReservations
```

* pod를 재배포 한다.

kubectl rollout restart deployment myreservation  -n huijun


* siege의 결과 (일부 요청이 실패로 처리된다.)
![무준단재배포 실패](https://user-images.githubusercontent.com/53825723/131072563-66762551-fd37-4131-b8f4-4996f2103179.JPG)

* Readiness 설정이 있는 경우(deployment에서 Readiness 설정을 추가한 후 배포한다.)
```
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
```
```
kubectl apply -n huijun -f MyReservation/kubernetes/deployment.yml -n huijun
```
* siege로 부하테스트를 한다. (워크로드 1000명, 1분)
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c1000 -t60S  -v http://myreservation:8080/myReservations
```

* pod를 재배포 한다.
kubectl rollout restart deployment myreservation  -n huijun

* siege의 결과 ( 모든 요청이 성공한다.)
![무준단재배포 성공](https://user-images.githubusercontent.com/53825723/131072557-7644e669-3b08-4cf3-b4bd-1399588f3332.JPG)

* Readiness 설정을 통해 무정지 재배포를 구현한다.


## Self-healing (Liveness Probe)
<!-- 
* order 서비스 deployment.yml   livenessProbe 설정을 port 8089로 변경 후 배포 하여 liveness probe 가 동작함을 확인 
```
    livenessProbe:
      httpGet:
        path: '/actuator/health'
        port: 8089
      initialDelaySeconds: 5
      periodSeconds: 5
``` 

![image](https://user-images.githubusercontent.com/5147735/109740864-4fcb2880-7c0f-11eb-86ad-2aabb0197881.png)
![image](https://user-images.githubusercontent.com/5147735/109742082-c0734480-7c11-11eb-9a57-f6dd6961a6d2.png)-->


* pod에 연결이 불가능할 경우 
    * 8090포트로 요청해야 하는 경우 가정
    * Deployment.yaml
```yaml
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8090
            initialDelaySeconds: 60
            timeoutSeconds: 2
            periodSeconds: 5
```
* Pod를 계속 재시작 한다.

![Liveness](https://user-images.githubusercontent.com/53825723/131075307-5c1d1b88-ab90-47e7-be08-e2db0390d2c1.JPG)

* Pod에 연결이 가능할 경우  
    * 8080포트로 상태 확인
```yaml
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 60
            timeoutSeconds: 2
            periodSeconds: 5
```

* Pod가 정상적으로 띄워진다.

![Liveness성공](https://user-images.githubusercontent.com/53825723/131075311-d00cabb0-e30e-4311-8fbf-d731efe307c5.JPG)

* Liveness 설정이 안되어 있는 경우 Pod의 상태는 Running 이지만 연결이 불가능 할 수 있다.




