package com.marvel.hospitality.reservationservice.e2e;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.dto.ReservationRequest;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.enumtype.ReservationStatus;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import com.marvel.hospitality.reservationservice.scheduler.ReservationScheduler;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


import static com.marvel.hospitality.reservationservice.enumtype.PaymentMode.BANK_TRANSFER;
import static com.marvel.hospitality.reservationservice.enumtype.RoomSegment.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@EnableKafka
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"bank-transfer-payment-update"})
@DirtiesContext
class FullFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ReservationRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ReservationScheduler scheduler;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;
    @Test
    void create_bankTransfer_confirmViaKafka_thenCancelIfOverdue() throws Exception {
        var request = new ReservationRequest("John", "101", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 5),
                MEDIUM, BANK_TRANSFER, null);
        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<Reservation> reservations = repository.findAll();
        Reservation pending = reservations.getFirst();
        String reservationId = pending.getId();
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);


        // 2. Send Kafka confirmation
        PaymentUpdateEvent event = new PaymentUpdateEvent("pay1", "acc1", BigDecimal.TEN, "E2E1234567 " + reservationId);
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("bank-transfer-payment-update", message);


        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker,
                "test-group",
                true);


  /*      Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();*/

       // embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "bank-transfer-payment-update");

        // When

        // Then
       /* ConsumerRecord<String, String> received =
                KafkaTestUtils.getSingleRecord(consumer, "bank-transfer-payment-update");
*/
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Reservation updated = repository.findById(reservationId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        });

        // 3. Make overdue and run scheduler
        pending.setStartDate(LocalDate.now().minusDays(1));
        repository.save(pending);


        scheduler.cancelOverdueBankTransferReservations();

        Reservation cancelled = repository.findById(reservationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }
}