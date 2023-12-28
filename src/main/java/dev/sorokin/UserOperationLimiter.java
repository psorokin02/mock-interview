import jakarta.annotation.Nonnull;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис, определяющий резрешено ли пользователю делать операцию на основе лимитов операций
 */
public class UserOperationLimiter {

    private final Map<OperationType, Long> operationsLimits;
    private final Map<String, Map<OperationType, Queue<Long>>> userOperations;

    public UserOperationLimiter(
            @Nonnull List<User> users,
            @Nonnull Long readLimitPerMinute,
            @Nonnull Long writeLimitPerMinute
    ) {
        this.operationsLimits = Map.of(
                OperationType.READ, readLimitPerMinute,
                OperationType.WRITE, writeLimitPerMinute
        );
        this.userOperations = users.stream()
                .collect(Collectors.toMap(User::getId, user -> Map.of(
                        OperationType.READ, new LinkedList<>(),
                        OperationType.WRITE, new LinkedList<>()
                )));
    }

    /**
     * Проверяет допустимо ли пользователю совершать операцию.
     * Принимает решение на основе лимитов для разных типов операций
     *
     * @param operation операцию
     * @return допустимо ли совершать операцию
     */
    public boolean isUserAllowedToPerform(@Nonnull Operation operation) {
        var userOperationsQueue = Optional.ofNullable(userOperations.get(operation.getUserId()))
                .map(it -> it.get(operation.getOperationType()))
                .orElseThrow(() -> new IllegalArgumentException("No such user"));

        removeExpiredOperationsFromQueue(userOperationsQueue);
        return userOperationsQueue.size() < operationsLimits.get(operation.getOperationType());
    }

    /**
     * Обработать сделанную операцию пользователем
     * @param operation операция
     */
    public void processPerformedOperation(@Nonnull Operation operation) {
        var userOperationsQueue = Optional.ofNullable(userOperations.get(operation.getUserId()))
                .map(it -> it.get(operation.getOperationType()))
                .orElseThrow(() -> new IllegalArgumentException("No such user"));
        userOperationsQueue.add(getCurrentTimeSeconds());
    }

    private void removeExpiredOperationsFromQueue(
            Queue<Long> userOperationsTimeQueue
    ){
        var timeLimit = getCurrentTimeSeconds() - 60L;
        var firstOperationTime = userOperationsTimeQueue.poll();
        while (firstOperationTime != null && firstOperationTime < timeLimit){
            firstOperationTime = userOperationsTimeQueue.poll();
        }
    }

    /**
     * Получить текущее время в секундах
     */
    @Nonnull
    public Long getCurrentTimeSeconds() {
        // Не надо реализовывать
        return 0L;
    }
}

/**
 * Операция, совершаемая пользователем
 */
@Data
class Operation {
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
class User {
    /**
     * id пользователя
     */
    private final String id;
    /**
     * login пользователя
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