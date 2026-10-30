package com.finora.app.planning;

import org.junit.jupiter.api.Test;
import com.finora.app.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanningStateServiceTest {
 @Test void doesNotPermitDeletingAnotherUsersPayment(){
   var profiles=mock(PlanningProfileRepository.class);
   var payments=mock(PlanningPaymentRepository.class);
   var service=new PlanningStateService(profiles,payments,new PlanningService());
   when(payments.findById(7L)).thenReturn(Optional.of(new PlanningPayment(999L,"Alquiler",LocalDate.now().plusDays(3),new BigDecimal("200.00"))));
   assertThrows(ApiException.class,()->service.deletePayment(123L,7L));
   verify(payments,never()).delete(any());
 }
 @Test void rejectsNegativeOrUnreasonableBalance(){
   var service=new PlanningStateService(mock(PlanningProfileRepository.class),mock(PlanningPaymentRepository.class),new PlanningService());
   assertThrows(ApiException.class,()->service.saveProfile(1L,new PlanningStateService.ProfileInput(new BigDecimal("-1"),BigDecimal.ZERO,LocalDate.now().plusDays(5))));
 }
}
