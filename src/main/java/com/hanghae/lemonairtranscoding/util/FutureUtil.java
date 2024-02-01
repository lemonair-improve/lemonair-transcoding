package com.hanghae.lemonairtranscoding.util;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FutureUtil {

	public static void setUpFuture(SupplierWithException<?> task, String successLog, String failureLog, int maxAttempts,
		long delayMillis) {
		retryOnFailure(() -> task, maxAttempts, delayMillis).thenAccept(result -> log.info(successLog))
			.exceptionally(throwable -> {
				log.error(failureLog);
				return null;
			});
	}

	public static <T> CompletableFuture<T> retryOnFailure(SupplierWithException<T> task, int maxAttempts,
		long delayMillis) {
		CompletableFuture<T> future = new CompletableFuture<>();
		retryOnFailureRecursive(task, maxAttempts, delayMillis, future);
		return future;
	}

	private static <T> void retryOnFailureRecursive(SupplierWithException<T> task, int attemptLeft, long delayMillis,
		CompletableFuture<T> future) {
		CompletableFuture.runAsync(() -> {
			try {
				T result = task.get();
				log.info("future.complete :" + result.toString());
				future.complete(result);
			} catch (Exception e) {
				if (attemptLeft > 0) {
					log.info("future.complete 실패 : 남은 재시도 횟수 " + attemptLeft);
					SleepUtil.sleepByMillis(delayMillis);
					retryOnFailureRecursive(task, attemptLeft - 1, delayMillis, future);
				} else {
					future.completeExceptionally(e);
				}
			}
		});
	}

	@FunctionalInterface
	public interface SupplierWithException<T> {
		T get() throws Exception;
	}
}
