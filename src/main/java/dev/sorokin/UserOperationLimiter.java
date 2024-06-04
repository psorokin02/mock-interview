package dev.sorokin;

import jakarta.annotation.Nonnull;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Сервис, определяющий резрешено ли пользователю делать операцию на основе лимитов операций
 */
public class UserOperationLimiter {

    private final Map<OperationType, Map<String, Queue<ProcessedOperation>>> processedOperationQueueMap;
    private final Long readLimitPerMinute;
    private final Long writeLimitPerMinute;

    public UserOperationLimiter(
            @Nonnull List<User> users,
            @Nonnull Long readLimitPerMinute,
            @Nonnull Long writeLimitPerMinute
    ) {
        Map<String, Queue<ProcessedOperation>> readOperations = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> new ConcurrentLinkedQueue<>()
                ));
        Map<String, Queue<ProcessedOperation>> writeOperations = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> new ConcurrentLinkedQueue<>()
                ));
        this.processedOperationQueueMap = Map.of(
                OperationType.READ, readOperations,
                OperationType.WRITE, writeOperations
        );
        this.readLimitPerMinute = readLimitPerMinute;
        this.writeLimitPerMinute = writeLimitPerMinute;
    }

    /**
     * Проверяет допустимо ли пользователю совершать операцию.
     * Принимает решение на основе лимитов для разных типов операций
     *
     * @param operation операцию
     * @return допустимо ли совершать операцию
     */
    public boolean isUserAllowedToPerform(@Nonnull Operation operation) {
        Queue<ProcessedOperation> queue = processedOperationQueueMap
                .get(operation.operationType)
                .get(operation.userId);

        if (queue == null) {
            throw new IllegalArgumentException("No such user with id: %s".formatted(operation.userId));
        }

        removeOldOperations(queue);
        int operationCounter = queue.size();

        if (operation.operationType.equals(OperationType.READ)) {
            return operationCounter < readLimitPerMinute;
        }
        return operationCounter < writeLimitPerMinute;
    }

    /**
     * Обработать сделанную операцию пользователем
     * @param operation операция
     */
    public void processPerformedOperation(@Nonnull Operation operation) {
        Queue<ProcessedOperation> queue = processedOperationQueueMap
                .get(operation.operationType)
                .get(operation.getUserId());

        if (queue == null) {
            throw new IllegalArgumentException("No such user with id: %s".formatted(operation.userId));
        }

        queue.add(new ProcessedOperation(
                operation.userId,
                operation.operationType,
                getCurrentTimeSeconds()
        ));
    }

    /**
     * Получить текущее время в секундах
     */
    @Nonnull
    public Long getCurrentTimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private void removeOldOperations(Queue<ProcessedOperation> processedOperationQueue) {
        long currentTime = getCurrentTimeSeconds();
        while (!processedOperationQueue.isEmpty()) {
            var currentOperation = processedOperationQueue.peek();
            if (currentOperation.operationTime < currentTime - 60) {
                processedOperationQueue.poll();
            } else {
                break;
            }
        }
    }

    /**
     * Информация о совершенной операции пользователем
     */
    @Data
    public static class ProcessedOperation {
        /**
         * Id, пользователя совершающего операцию
         */
        private final String userId;
        /**
         * Тип операции
         */
        private final OperationType operationType;
        /**
         * Время совершения операции
         */
        private final Long operationTime;
    }

    /**
     * Операция, совершаемая пользователем
     */
    @Data
    public static class Operation {
        /**
         * Id, пользователя совершающего операцию
         */
        private final String userId;
        /**
         * Тип операции
         */
        private final OperationType operationType;
    }

    /**
     * Пользователь
     */
    @Data
    public static class User {
        /**
         * Id пользователя
         */
        private final String id;
        /**
         * Login пользователя
         */
        private final String login;
    }

    /**
     * Типы операций пользователя
     */
    enum OperationType {
        /**
         * Операция чтения
         */
        READ,
        /**
         * Операция записи
         */
        WRITE
    }
}