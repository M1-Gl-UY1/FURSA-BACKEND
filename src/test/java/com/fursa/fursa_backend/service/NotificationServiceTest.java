package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.NotificationResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Notification;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationService notificationService;

    private Investisseur alice;

    @BeforeEach
    void setUp() {
        alice = new Investisseur();
        alice.setId(1L);
    }

    @Test
    void envoyer_creeNotificationNonLue() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(42L);
            return n;
        });

        Notification n = notificationService.envoyer(alice, "Titre", "Message", TypeMessage.TRANSACTION);

        assertEquals(42L, n.getId());
        assertEquals(alice, n.getDestinataire());
        assertEquals(TypeMessage.TRANSACTION, n.getType());
        assertFalse(n.getLu());
    }

    @Test
    void listerPour_retourneToutes() {
        Notification n1 = creerNotification(1L, true);
        Notification n2 = creerNotification(2L, false);
        when(notificationRepository.findByDestinataireIdOrderByDateDesc(1L))
                .thenReturn(List.of(n2, n1));

        List<NotificationResponse> res = notificationService.listerPour(1L);
        assertEquals(2, res.size());
    }

    @Test
    void listerNonLues_filtreSurLuFalse() {
        Notification nonLue = creerNotification(3L, false);
        when(notificationRepository.findByDestinataireIdAndLuFalseOrderByDateDesc(1L))
                .thenReturn(List.of(nonLue));

        List<NotificationResponse> res = notificationService.listerNonLues(1L);
        assertEquals(1, res.size());
        assertFalse(res.get(0).lu());
    }

    @Test
    void marquerLue_passeAVrai() {
        Notification n = creerNotification(10L, false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse res = notificationService.marquerLue(10L);
        assertTrue(res.lu());
    }

    @Test
    void marquerLue_inexistant_leveNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> notificationService.marquerLue(999L));
    }

    private Notification creerNotification(Long id, Boolean lu) {
        Notification n = new Notification();
        n.setId(id);
        n.setTitre("T" + id);
        n.setMessage("M" + id);
        n.setType(TypeMessage.INFO);
        n.setDate(LocalDateTime.now());
        n.setLu(lu);
        n.setDestinataire(alice);
        return n;
    }
}
