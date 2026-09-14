package br.dev.ctrls.itsm.modules.access.domain.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Serviço de domínio puro responsável pelo cálculo de janelas de tempo e fusos horários
 * para liberação de catracas físicas do edifício.
 */
@Component
public class AccessWindowCalculator {

    public static final ZoneId CLINIC_ZONE = ZoneId.of("America/Sao_Paulo");
    public static final DateTimeFormatter GERACESSO_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record Window(
        LocalDate date,
        LocalTime openingTime,
        LocalTime closingTime,
        String startVisitFormatted,
        String endVisitFormatted
    ) {}

    /**
     * Calcula a data efetiva do agendamento (ajustando para hoje caso esteja retroativa).
     */
    public LocalDate resolveAppointmentDate(LocalDate appointmentDate) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        if (appointmentDate == null || appointmentDate.isBefore(today)) {
            return today;
        }
        return appointmentDate;
    }

    /**
     * Janela padrão de liberação física para consultas (06:00 às 23:00).
     */
    public Window calculateStandardWindow(LocalDate appointmentDate) {
        LocalDate date = resolveAppointmentDate(appointmentDate);
        LocalTime opening = LocalTime.of(6, 0);
        LocalTime closing = LocalTime.of(23, 0);

        String start = LocalDateTime.of(date, opening).format(GERACESSO_DATE_FORMATTER);
        String end = LocalDateTime.of(date, closing).format(GERACESSO_DATE_FORMATTER);

        return new Window(date, opening, closing, start, end);
    }

    /**
     * Janela de auto-cadastro / quiosque (06:00 às 23:59).
     */
    public Window calculateSelfRegistrationWindow(LocalDate appointmentDate) {
        LocalDate date = resolveAppointmentDate(appointmentDate);
        LocalTime opening = LocalTime.of(6, 0);
        LocalTime closing = LocalTime.of(23, 59);

        String start = LocalDateTime.of(date, opening).format(GERACESSO_DATE_FORMATTER);
        String end = LocalDateTime.of(date, closing).format(GERACESSO_DATE_FORMATTER);

        return new Window(date, opening, closing, start, end);
    }
}
