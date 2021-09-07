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
![image](https://user-images.githubusercontent.com/86760622/132291700-fee10421-8b34-47f4-bcca-f3d811d6e1e6.png)



# 헥사고날 아키텍처 다이어그램 도출
![image](https://user-images.githubusercontent.com/86760622/132291981-d20fb41f-8799-4115-98b3-da1d42825ac3.png)


# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다. (각각의 포트넘버는 8080 ~ 8083이다)
```
cd gateway
mvn spring-boot:run

cd reservation
mvn spring-boot:run

cd approval
mvn spring-boot:run

cd mycourt
mvn spring-boot:run

```

## DDD 의 적용
msaez.io를 통해 구현한 Aggregate 단위로 Entity를 선언 후, 구현을 진행하였다.
Entity Pattern과 Repository Pattern을 적용하기 위해 Spring Data REST의 RestRepository를 적용하였다.

**Reservation 서비스의 Reservation.java**
```java 

package wimbledontenniscourt;

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
    private String courtName;
    private String playerName;
    private String time;
    private String status;

    @PostPersist
    public void onPostPersist(){
        Reserved reserved = new Reserved();
        BeanUtils.copyProperties(this, reserved);
        reserved.publishAfterCommit();

    }
    @PreUpdate
    public void onPreUpdate(){
        CancledReservation cancledReservation = new CancledReservation();
        BeanUtils.copyProperties(this, cancledReservation);
        cancledReservation.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        wimbledontenniscourt.external.Approval approval = new wimbledontenniscourt.external.Approval();
        // mappings goes here
        ReservationApplication.applicationContext.getBean(wimbledontenniscourt.external.ApprovalService.class)
            //.cancelApproval(approval);
            //.cancelApproval(this.getId(), approval);
            .cancelApproval(this.getId());

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getCourtName() {
        return courtName;
    }

    public void setCourtName(String courtName) {
        this.courtName = courtName;
    }
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}

```

**Approval 서비스의 PolicyHandler.java**
```java

package wimbledontenniscourt;

import wimbledontenniscourt.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired ApprovalRepository approvalRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserved_Receive(@Payload Reserved reserved){

        if(!reserved.validate()) return;

        System.out.println("\n\n##### listener Receive : " + reserved.toJson() + "\n\n");

        // Sample Logic //
        Approval approval = new Approval();
        approval.setCourtName(reserved.getCourtName());
        approval.setPlayerName(reserved.getPlayerName());
        approval.setReservationId(reserved.getId());
        approval.setTime(reserved.getTime());
        approval.setStatus(reserved.getStatus());
        approvalRepository.save(approval);

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}

```


**Approval 서비스의 Approval.java**
```java

package wimbledontenniscourt;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Approval_table")
public class Approval {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String courtName;
    private String playerName;
    private String time;
    private String status;
    private Long reservationId;

    @PostPersist
    public void onPostPersist(){
    }
    @PostUpdate
    public void onPostUpdate(){

        System.out.println("\n\n##### STATUS : "+this.getStatus()+"\n\n");
        if (this.getStatus().equals("approved")){
            Approved approved = new Approved();
            BeanUtils.copyProperties(this, approved);
            approved.publishAfterCommit();
            System.out.println("\n\n##### Approved Created : " + approved.toJson() + "\n\n");
        }else if (this.getStatus().equals("cancled reservation")){
            CancledApproval cancledApproval = new CancledApproval();
            BeanUtils.copyProperties(this, cancledApproval);
            cancledApproval.publishAfterCommit();
            System.out.println("\n\n##### Approval Cancled : " + cancledApproval.toJson() + "\n\n");
        }else{
            System.out.println("\n\n##### STATUS IS NOT ACCEPTABLE!! : " + this.getStatus() + "\n\n");
        }

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getCourtName() {
        return courtName;
    }

    public void setCourtName(String courtName) {
        this.courtName = courtName;
    }
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

}


```
**Mycourt 서비스의 MycourtViewHandler.java**
```java
package wimbledontenniscourt;

import wimbledontenniscourt.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class MycourtViewHandler {


    @Autowired
    private MycourtRepository mycourtRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserved_then_CREATE_1 (@Payload Reserved reserved) {
        try {

            if (!reserved.validate()) return;

            // view 객체 생성
            Mycourt mycourt = new Mycourt();
            // view 객체에 이벤트의 Value 를 set 함
            mycourt.setReservationId(reserved.getId());
            mycourt.setCourtName(reserved.getCourtName());
            mycourt.setPlayerName(reserved.getPlayerName());
            mycourt.setTime(reserved.getTime());
            mycourt.setStatus(reserved.getStatus());
            // view 레파지 토리에 save
            mycourtRepository.save(mycourt);

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenApproved_then_UPDATE_1(@Payload Approved approved) {
        try {
            if (!approved.validate()) return;
                // view 객체 조회
                System.out.println("\n\n##### listener UpdateCourt view handler : " + approved.toJson() + "\n\n");

                    List<Mycourt> mycourtList = mycourtRepository.findByReservationId(approved.getReservationId());
                    for(Mycourt mycourt : mycourtList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mycourt.setApprovalId(approved.getId());
                    mycourt.setStatus(approved.getStatus());
                // view 레파지 토리에 save
                mycourtRepository.save(mycourt);
                }

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenCancledReservation_then_UPDATE_2(@Payload CancledReservation cancledReservation) {
        try {
            if (!cancledReservation.validate()) return;
                // view 객체 조회

                    List<Mycourt> mycourtList = mycourtRepository.findByReservationId(cancledReservation.getId());
                    for(Mycourt mycourt : mycourtList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mycourt.setStatus(cancledReservation.getStatus());
                // view 레파지 토리에 save
                mycourtRepository.save(mycourt);
                }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

}

```



**Mycourt 서비스의 Mycourt.java**
```java

package wimbledontenniscourt;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name="Mycourt_table")
public class Mycourt {

        @Id
        @GeneratedValue(strategy=GenerationType.AUTO)
        private Long id;
        private Long reservationId;
        private Long approvalId;
        private String courtName;
        private String playerName;
        private String time;
        private String status;


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
        public Long getApprovalId() {
            return approvalId;
        }

        public void setApprovalId(Long approvalId) {
            this.approvalId = approvalId;
        }
        public String getCourtName() {
            return courtName;
        }

        public void setCourtName(String courtName) {
            this.courtName = courtName;
        }
        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }
        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
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

![image](https://user-images.githubusercontent.com/86760622/132295477-363b257b-e417-4539-9764-9bf945449753.png)

- Approval 서비스 호출 결과 

![image](https://user-images.githubusercontent.com/86760622/132295553-ca6b582f-570f-4a3d-b383-4add2321be1c.png)


- Mycourt 서비스 호출 결과

![image](https://user-images.githubusercontent.com/86760622/132295583-1cc97f86-9cdf-45fd-8773-867ad24d1632.png)



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
        - id: reservation
          uri: http://localhost:8081
          predicates:
            - Path=/reservations/** 
        - id: approval
          uri: http://localhost:8082
          predicates:
            - Path=/approvals/** 
        - id: mycourt
          uri: http://localhost:8083
          predicates:
            - Path= /mycourts/**
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
8080 port로 Reservation, Approval, Mycourt 서비스 정상 호출

![image](https://user-images.githubusercontent.com/86760622/132295762-7cfe7b17-cdee-4b2a-96c2-d152fbf14e3c.png)

![image](https://user-images.githubusercontent.com/86760622/132295859-8b306d05-4bb6-4621-805e-cf599855c158.png)

![image](https://user-images.githubusercontent.com/86760622/132295902-20e772eb-8ba3-4699-b575-12a8a373aea8.png)



# CQRS/saga/correlation
Materialized View를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이)도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다. 
본 프로젝트에서 View 역할은 MyReservation 서비스가 수행한다.

예약 실행 후 Approval, Mycourt 화면 - reserved 상태로 예약정보 등록

![image](https://user-images.githubusercontent.com/86760622/132296041-95e5d409-2562-4401-931f-1ede271a3e77.png)

![image](https://user-images.githubusercontent.com/86760622/132296134-c11bb73d-0dd6-4967-a275-cc352e5c5367.png)

![image](https://user-images.githubusercontent.com/86760622/132296181-b3b662da-d1bd-4256-919a-50c88624df0a.png)



승인 후 Mycourt 화면 - approved 상태로 변경

![image](https://user-images.githubusercontent.com/86760622/132296512-f9474466-bab1-4350-a734-4e1be579240a.png)

![image](https://user-images.githubusercontent.com/86760622/132296573-4b4bac3c-e09c-4570-8078-dfce5742ee2b.png)



예약취소 후 Approval, Mycourt 화면 - Resevation.Mycourt서비스의 Canceled Reservation 상태로 변경되고 Approval은 삭제됨

![image](https://user-images.githubusercontent.com/86760622/132296883-12364169-48af-4e7f-b650-e31e07d3dc35.png)

![image](https://user-images.githubusercontent.com/86760622/132296930-b4f56477-89b9-40a5-b92c-91be02f269f6.png)

![image](https://user-images.githubusercontent.com/86760622/132296991-fb5bb789-2673-46e3-881e-3e98833b2cc7.png)



위와 같이 예약을 하게되면 Reservation > Approval > Mycourt로 예약이 Assigned 되고

예약 취소가 되면 Status가 Cancelled Reservation로 Update 되는 것을 볼 수 있다.

또한 Correlation을 Key를 활용하여 Id를 Key값을 하고 원하는 예약하고 서비스간의 공유가 이루어 졌다.

위 결과로 서로 다른 마이크로 서비스 간에 트랜잭션이 묶여 있음을 알 수 있다.

# 폴리글랏
Reservation 서비스의 DB와 MyReservation의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.

**Reservation의 pom.xml DB 설정 코드**

![image](https://user-images.githubusercontent.com/86760622/132297146-5db8c85d-3bcf-4916-ad55-6495c8a958d6.png)

**Mycourt의 pom.xml DB 설정 코드**

![image](https://user-images.githubusercontent.com/86760622/132297197-c916f675-207f-4964-9b7b-16cc87fdbd1b.png)



# 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 예약취소(Reservation)와 승인취소(Approval)간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하도록 한다.

**Reservation 서비스 내 external.ApprovalService.java**
```java

package wimbledontenniscourt.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

//@FeignClient(name="approval", url="${api.url.pay}", fallback=ApprovalServiceImpl.class)
@FeignClient(name="approval", url="${api.url.pay}")
public interface ApprovalService {
    @RequestMapping(method= RequestMethod.DELETE, path="/approvals/{id}")
    public void cancelApproval(@PathVariable long id);

}


```

**동작 확인**

Approval 서비스 중지함

![image](https://user-images.githubusercontent.com/86760622/132298317-be8e2093-e5ae-4fcf-bff2-6add2a48e8f3.png)


예약취소시 Approval 서비스 중지로 인해 예약 실패

![image](https://user-images.githubusercontent.com/86760622/132298549-78fcc13f-9f5f-4d6e-91ec-102cfedf07fa.png)


Approval 서비스 재기동 후 예약취소 성공함

![image](https://user-images.githubusercontent.com/86760622/132298821-bf98897a-a2bd-4995-81c2-29f6e63bf930.png)


Approval 서비스 조회시 정상적으로 예약취소로 데이터가 삭제되어 있음

![image](https://user-images.githubusercontent.com/86760622/132299216-f544bc71-5656-4922-8d6e-f8a4a6729774.png)


Fallback 설정 
- external.ApprovalService.java

```java

package wimbledontenniscourt.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="approval", url="${api.url.pay}", fallback=ApprovalServiceImpl.class)
//@FeignClient(name="approval", url="${api.url.pay}")
public interface ApprovalService {
    @RequestMapping(method= RequestMethod.DELETE, path="/approvals/{id}")
    public void cancelApproval(@PathVariable long id);

}


```
- external.ApprovalServiceImpl.java
```java
package wimbledontenniscourt.external;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApprovalServiceImpl implements ApprovalService {
        public void cancelApproval(long id){
            System.out.println("\n\n ######  승인서비스 지연중입니다.    #######");
            System.out.println("######  잠시 뒤에 다시 시도해주세요.     #######");
            System.out.println("######  승인서비스 지연중입니다.    #######\n\n");
        }
}


```

Fallback 결과(Pay service 종료 후 예약실행 추가 시)

![image](https://user-images.githubusercontent.com/86760622/132300121-28d2cdcb-3e4a-4e62-9b92-a3b89b9dcb10.png)


# 운영

## CI/CD
* 카프카 설치
```
- 헬름 설치
참고 : http://msaschool.io/operation/implementation/implementation-seven/
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 > get_helm.sh
chmod 700 get_helm.sh
./get_helm.sh

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
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --topic wimbledontenniscourt --create --partitions 1 --replication-factor 1
```
* Topic 확인
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --list
```
* 이벤트 발행하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-producer --broker-list my-kafka:9092 --topic wimbledontenniscourt
```
* 이벤트 수신하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-consumer --bootstrap-server my-kafka:9092 --topic wimbledontenniscourt
```

* 소스 가져오기
```
git clone https://github.com/khosmi/wimbledontenniscourt.git
```

## Deploy / Pipeline

* Azure 레지스트리에 도커 이미지 push, deploy, 서비스생성(yml파일 이용한 deploy)
```
# 각 마이크로 서비스의 deployment에서 이미지 수정 필요
# label과 이미지 이름 소문자로 변경 필요


cd approval
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user01acr1.azurecr.io/approval .
# acr에 이미지 푸시
docker push user01acr1.azurecr.io/approval
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포 
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment approval  
cd ..

cd reservation
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user01acr1.azurecr.io/reservation .
# acr에 이미지 푸시
docker push user01acr1.azurecr.io/reservation
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포 
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment reservation  
cd ..

cd mycourt
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user01acr1.azurecr.io/mycourt .
# acr에 이미지 푸시
docker push user01acr1.azurecr.io/mycourt
# kubernetes에 service, deployment 배포
kubectl apply -f kubernetes
# Pod 재배포
# Deployment가 변경되어야 새로운 이미지로 Pod를 실행한다.
# Deployment가 변경되지 않아도 새로운 Image로 Pod 실행하기 위함
kubectl rollout restart deployment mycourt  
cd ..

cd gateway
# jar 파일 생성
mvn package
# 이미지 빌드
docker build -t user01acr1.azurecr.io/gateway .
# acr에 이미지 푸시
docker push user01acr1.azurecr.io/gateway
# kubernetes에 service, deployment 배포
kubectl create deploy gateway --image=user01acr1.azurecr.io/gateway   
kubectl expose deploy gateway --type=LoadBalancer --port=8080 

kubectl rollout restart deployment gateway
cd ..

```
* Service, Pod, Deploy 상태 확인

![image](https://user-images.githubusercontent.com/86760622/132300923-69b0c1bc-f757-4263-bf23-68c7b585903d.png)


* deployment.yml  참고

![image](https://user-images.githubusercontent.com/86760622/132301250-baa0ba10-4f0a-49a5-928a-79afe733b1e3.png)


## 서킷 브레이킹
* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함
* 예약 취소시 Reservation -> Approval과의 Req/Res 연결에서 요청이 과도한 경우 CirCuit Breaker 통한 격리
* Hystrix 를 설정: 요청처리 쓰레드에서 처리시간이 1500 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
* 삭제만 Req/Res 방식으로 개발을 했기 때문에 Siege 부하를 주기위해서는 많은 데이터가 생성되어야 하며 id를 변수화 하며 호출해야함
* 따라서, 예약 등록시 Pub/Sub 방식을  Req/Res 방식으로 변경하고 Test 를 수행함

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
// Reservation 서비스 ApprovalService.java

//@FeignClient(name="approval", url="${api.url.pay}", fallback=ApprovalServiceImpl.class)
@FeignClient(name="approval", url="${api.url.pay}")   // fallback 제외
public interface ApprovalService {
    @RequestMapping(method= RequestMethod.DELETE, path="/approvals/{id}")
    public void cancelApproval(@PathVariable long id);

    @RequestMapping(method= RequestMethod.POST, path="/approvals")
    public void createApproval(@RequestBody Approval approval);
    
```


```
// Approval 서비스 Approval.java

    @PostPersist
    public void onPostPersist(){

        try {
            Thread.currentThread().sleep((long) (1000 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
    }


    @PreRemove
    public void onPreRemove(){
        CancledApproval cancledApproval = new CancledApproval();
        BeanUtils.copyProperties(this, cancledApproval);
        cancledApproval.publishAfterCommit();

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
/home/project/tennis/siege/kubectl apply -f siege.yaml
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 100명 30초 동안 실시
* --> fallback 미설정시 오류 발생
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c100 -t30S  -v --content-type "application/json" 'http://10.0.241.98:8080/reservations PATCH {"status":"cancled reservation"}'
```

![image](https://user-images.githubusercontent.com/86760622/132310761-f7b4bdc8-77ef-4c92-93e6-ea647e34b6c5.png)

* fallback 설정 후 다시 부하테스트 수행함  --> 100% 로 응답 수행되며, 서비스 불가 메세지 출력됨

![image](https://user-images.githubusercontent.com/86760622/132314955-e9fc2d9b-6fc6-48bf-a255-565121161a24.png)

![image](https://user-images.githubusercontent.com/86760622/132315098-543c2b90-95b7-4766-be23-f72775f862ff.png)



## ConfigMap
* mycourt 실행할 때 환경변수 사용하여 활성 프로파일을 설정한다.
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
![image](https://user-images.githubusercontent.com/86760622/132315443-e991f357-3236-4f09-a991-4ca02b08b4bb.png)



* 다시 배포한다.
```
mvn package
docker build -t user01acr1.azurecr.io/mycourt .
docker push user01acr1.azurecr.io/mycourt
kubectl apply -f kubernetes
```

* pod의 로그 확인
```
kubectl logs -f pod/mycourt-9b5ff558f-nt6q6
```
![image](https://user-images.githubusercontent.com/86760622/132316105-ba394702-2186-428a-9a85-f4fa64f19acc.png)


* pod의 sh에서 환경변수 확인
```
kubectl exec mycourt-9b5ff558f-nt6q6 -it -- sh
```
![image](https://user-images.githubusercontent.com/86760622/132316378-c826b679-af1c-4475-a370-90050974b7d3.png)


## 오토스케일 아웃
* 앞서 서킷 브레이커(CB) 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다.

*  mycourt 서비스 deployment.yml 설정
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
kubectl autoscale deployment mycourt --cpu-percent=15 --min=1 --max=10
```
```
kubectl get hpa
```
![image](https://user-images.githubusercontent.com/86760622/132316724-e596ab19-5f97-4a5a-aa65-4294a08a2e43.png)


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
siege -c1000 -t60S  -v http://mycourt:8080/mycourts
```

* 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```
kubectl get deploy mycourt -w
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

![image](https://user-images.githubusercontent.com/86760622/132317328-025601c2-d1ad-4382-b55a-7b0dbcf45b81.png)

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




