package com.hanghae.lemonairtranscoding.learntest;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadLearnTest {

	@Test
	void startThread() {
		Thread thread = new CustomThread();
		thread.start();
		log.info("");
	}

	@Test
	void startThreadWithOutRunMethod() {
		// 쓰레드는 시작되었지만, 아무 작업을 수행하지 않는다.
		Thread thread = new CustomThreadNotImplementedRunMethod();
		thread.start();
		log.info("");
	}

	static class CustomThread extends Thread {
		@Override
		public void run() {
			log.info("");
		}
	}

	static class CustomThreadNotImplementedRunMethod extends Thread {

	}
	@Test
	void runThreadWithoutStart0() {
		log.info("Calls to run() should be probably  be replaced with start()");
		Thread thread = new CustomThread();
		thread.run();
		log.info("");
	}

	@Test
	void runnable() {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				log.info("runnable의 run 메소드");
			}
		};

		Thread thread = new Thread(runnable);
		thread.start();
		log.info("메인쓰레드");
	}
	// 그런데 왜 thread.start()를 실행했는데, run 메소드가 override되어있고 되어있지 않고가 중요한걸까?
	// 우리는 우리가 정의한 run 메소드를 별도의 쓰레드로 하고 싶은데, run을 직접 호출하면 메인 쓰레드에서 해당 객체의 run 메소드를 직접 호출하는 것에 불과하고,
	// 별도의 쓰레드로 실행시키는 작업을 JVM의 도움이 필요하다.

	//thread.start()
	// 1. 쓰레드가 실행 가능한지 검사함
	// 2. 쓰레드를 ?쓰레드 그룹에 추가함
	// 3. 쓰레드를 JVM이 실행시킨다.

	// 쓰레드의 상태는, new, Runnable, Waiting, Timed Waiting, Terminated의 5가지 상태가 있다.
	// thread.start()를 실행했을때, 상태가 0(new)인 경우에만 쓰레드가 작업을 시작한다.
	// 쓰레드를 쓰레드 그룹에 추가하고 나서 쓰레드를 JVM이 실행시킬때는
	// private native void start0(); 이라는 네이티브 메소드를 실행한다.
	//native 예약어는 Java 언어에서 네이티브 메소드를 선언할 때 사용됩니다. 네이티브 메소드는 Java 언어로 작성된 코드가 아닌, 네이티브 코드로 작성된 함수를 호출하는 메소드입니다. 네이티브 코드는 주로 C, C++ 등의 언어로 작성되며, Java 네이티브 인터페이스 (JNI)를 통해 Java 코드에서 호출됩니다.
	//
	// native 예약어를 사용하여 선언된 메소드는 구현이 없으며, 해당 메소드의 내용은 네이티브 코드로 작성된다는 것을 나타냅니다. 네이티브 메소드를 사용하는 주된 이유는 Java 언어로는 효율적으로 구현하기 어려운 기능을 다른 언어로 작성된 코드를 활용하여 사용하고자 할 때입니다.
	// 내부적으로 start0() 메소드가 c언어로 구현되어있고

	@Test
	void callableVoid() {
		ExecutorService executorService = Executors.newSingleThreadExecutor();

		Callable<Void> callable = new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				log.info("callable called ");
				Thread.sleep(1000);
				log.info("after sleep ");
				return null;
			}
		};

		executorService.submit(callable);
		// 쓰레드 풀을 종료하기 전에 이미 submit 된 모든 작업을 마칠ㄸ ㅐ까지 기다린 후 종료된다.
		executorService.shutdown();

		try {
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

	}

	// Thread 클래스는 Runnable 함수형 인터페이스를 구현하고있고

	@Test
	void callableString() throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newSingleThreadExecutor();

		Callable<String> callable = new Callable<String>() {
			@Override
			public String call() throws Exception {
				return "thread called";
			}
		};

		Future<String> future = executorService.submit(callable);
		log.info("future.get() : " + future.get());
		executorService.shutdown();
	}

	// Runnable은 함수형인터페이스로써, 익명 객체로 사용할수있지만, Thread는 클래스를 상속받아서 구현해야한다.
	// Thread를 상속받으면 더많은 리소스를 사용해야하므로 Runnable이 주로 사용된다.

	@Test
	void executorExecuteWithMainThread() {
		//Executor 인터페이스는 쓰레드 풀의 구현을 위한 인터페이스이다.
		// 등록된 Runnable tasak를 실행하기 위한 인터페이스
		// 작업 등록과 작업 실행중 작업 실행만을 책임진다.
		Runnable runnable = () -> log.info("runnable run");
		Executor executor = new Executor() {
			@Override
			public void execute(Runnable command) {
				log.info("executor-execute 실행");
				command.run();
			}
		};

		executor.execute(runnable);
	}

	@Test
	void executorExecuteWithNewThread() {
		Runnable runnable = () -> log.info("runnable run");
		Executor executor = new Executor() {
			@Override
			public void execute(Runnable command) {
				log.info("exector-execute 실행");
				new Thread(command).start();
			}
		};
		executor.execute(runnable);
	}

	//Callable 인터페이스의 구현체인 task는 가용 가능한 쓰레드가 없어서 실행이 미뤄질 수 있고, 작업 시간이 오래 걸릴 수도 있다.
	// 그래서 실행 결과를 바로 받지 못하고 미래의 어떤 시점에 받을 수 있겠지만, 언제인지 모른다.
	// 미래에 완료된 Callable의 반환값을 구하기 위해 사용되는 것이 Future이다. 즉, Future는 비동기 작업을 갖고 있어 미래에 실행 결과를 얻도록 도와준다.
	// 이를 위해 비동기 작업의 현재 상태를 확인하고, 기다리며, 결과를 얻는 방법 등을 제공한다

	@Test
	void ExecutorService_shutdown() {
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		Runnable runnable = () -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			log.info("Runnable run 실행");
		};
		executorService.execute(runnable);
		executorService.shutdown();
		// shutdown하여 쓰레드풀의 라이프사이클을 직접, 새로운 작업들을 받아들이지 않고, graceful shutdown을 수행하게 된다.,
		// 그러므로 새로운 작업을 실행하라고 했을 때
		assertThatThrownBy(() -> executorService.execute(runnable)).isInstanceOf(RejectedExecutionException.class);
	}

	@Test
	void executorService_noShutdown() {
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		Runnable runnable = () -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			log.info("Runnable run 실행");
		};
		executorService.execute(runnable);
		// 원래는 앱이 종료되지 않아야하는데, shutdown 해주지않아서 Test 환경에서는 아무것도 실행하지않는다.
	}

	// ExecutorService는 작업 등록을 위한 인터페이스이다.,
	// ThreadPoolExecutor는 ExecutorService의 구현체이다.

	// ExecutorService는 이전에 학습한 Executor를 상속받아서
	// 작업 등록 및 실행을 위한 책임도 갖는다.

	// ExecutorServcie에는 라이프사이클 관리 기능, 비동기 작업을 위한 기능의 두가지 기능으로 분류할 수 잇는데,

	// 라이프사이클 관리 : shutdown (graceful Shutdown), shutdownNow, isShutdown, isTerminated, awaitTermination 등이 있다.

	@Test
	void shutdownNow() throws InterruptedException {
		Runnable runnable = () -> {
			while (true) {
				if (Thread.currentThread().isInterrupted()) {
					log.error("thread Interrupted");
					break;
				}
			}
			log.info("thread end");
		};

		ExecutorService executorService = Executors.newFixedThreadPool(2);

		for (int i = 0; i < 2; i++) {
			executorService.execute(runnable);
		}

		executorService.shutdownNow();
	}

	@Test
	void invokeAllTakesTimeTheLongestOfTasks() throws InterruptedException, ExecutionException {
		ExecutorService executorService = Executors.newFixedThreadPool(10);

		Callable<String> callable = () -> {
			Thread.sleep(1000);
			final String myWord = "I waited for 1 second";
			log.info("1 sec callable run");
			return myWord;
		};

		Callable<String> callable3 = () -> {
			Thread.sleep(3000);
			final String myWord = "I waited for 3 seconds";
			log.info("3 sec callable run");
			return myWord;
		};
		// 시작 시간 기록
		Instant start = Instant.now();

		List<Future<String>> futureList = executorService.invokeAll(Arrays.asList(callable, callable3));

		for (Future<String> future : futureList) {
			log.info("future.get() : " + future.get());
			log.info("future.isDone() : " + future.isDone());
		}

		log.info("time = " + Duration.between(start, Instant.now()).getSeconds());

		executorService.shutdown();
	}

	@Test
	void invokeAnyTakesTimeTheShortestOfTasks() throws InterruptedException, ExecutionException {
		ExecutorService executorService = Executors.newFixedThreadPool(10);

		Callable<String> callable = () -> {
			Thread.sleep(1000);
			final String myWord = "I waited for 1 second";
			log.info("1 sec callable run");
			return myWord;
		};

		Callable<String> callable3 = () -> {
			Thread.sleep(3000);
			final String myWord = "I waited for 3 seconds";
			log.info("3 sec callable run");
			return myWord;
		};
		// 시작 시간 기록
		Instant start = Instant.now();

		// 애초에 하나만 반환할 거니까 callable<T> 의 T를 리턴해버린다.
		String result = executorService.invokeAny(Arrays.asList(callable, callable3));

		log.info("result : " + result);

		log.info("time = " + Duration.between(start, Instant.now()).getSeconds());

		executorService.shutdown();
	}

	// 비동기 작업의 진행을 추적할 수 있도록 Future를 반환한다. Future 변수가 반환되었다면 모두 실행된 것이지만,
	// 작업이 정상저긍로 종료되었을 수도 있고, 예외의 의해 종료되었을 수도 있다. 예를들면 InterruptedException,

	//submit
	// 실행할 작업들을 추가하고, 작업의 상태와 결과를 포함하는 Future를 반환함
	// Future의 get을 호출하면 성공적으로 작업이 완료된 후 결과를 얻을 수 있음
	//
	// invokeAll
	// 모든 결과가 나올 때 까지 대기하는 블로킹 방식의 요청
	// 동시에 주어진 작업들을 모두 실행하고, 전부 끝나면 각각의 상태와 결과를 갖는 List<Future>을 반환함
	//
	// invokeAny
	// 가장 빨리 실행된 결과가 나올 때 까지 대기하는 블로킹 방식의 요청
	// 동시에 주어진 작업들을 모두 실행하고, 가장 빨리 완료된 하나의 결과를 Future로 반환받음

	@Test
	void scheduleAndRunSuccess() throws InterruptedException {

		Runnable runnable = () -> log.info("runnable run");
		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

		ScheduledFuture<?> scheduledFuture = executorService.schedule(runnable, 1, TimeUnit.SECONDS);

		Thread.sleep(2000L);
		executorService.shutdown();
		assertThat(scheduledFuture.isDone()).isTrue();
		assertThat(scheduledFuture.isCancelled()).isFalse();
	}

	@Test
	void scheduleAndRunFailure() throws InterruptedException {

		Runnable runnable = () -> log.info("runnable run");
		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

		ScheduledFuture<?> scheduledFuture = executorService.schedule(runnable, 1, TimeUnit.SECONDS);

		Thread.sleep(500L);
		executorService.shutdown();

		// 수행이 완료되지 않았고, Cancle된것도 아니다.
		assertThat(scheduledFuture.isDone()).isFalse();
		assertThat(scheduledFuture.isCancelled()).isFalse();
	}

	// ScheduledExecutorService 인터페이스
	// 특정 시간대에 작업을 실행하거나 주기적으로 ㅈ가업을 실행하고 싶을 때 등에 사용할 수 있다.

	// schedule
	// 특정 시간(delay) 이후에 작업을 실행시킴

	// scheduleAtFixedRate
	// 특정 시간(delay) 이후 처음 작업을 실행시킴
	// 작업이 실행되고 특정 시간마다 작업을 실행시킴
	//
	// scheduleWithFixedDelay
	// 특정 시간(delay) 이후 처음 작업을 실행시킴
	// 작업이 완료되고 특정 시간이 지나면 작업을 실행시킴

	@Test
	void scheduleAtFixedRate() throws InterruptedException {
		AtomicInteger runCount = new AtomicInteger();
		Runnable runnable = () -> {
			log.info("Start scheduleAtFixedRate:    " + LocalTime.now());
			log.info("task가 시작된 횟수 : " + runCount.incrementAndGet());
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			log.info("Finish scheduleAtFixedRate:    " + LocalTime.now());
		};
		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

		executorService.scheduleAtFixedRate(runnable, 0, 2, TimeUnit.SECONDS);

		// 0초, 2초, 4초, 6초에 각 task 를 수행하도록 하고, 총 실행횟수는 4회, task 수행에 걸리는 시간이 1초 이므로 완료된 task는 3개이다.
		Thread.sleep(6000L);
		executorService.shutdown();
	}

	@Test
	void scheduleWithFixedDelay() throws InterruptedException {
		AtomicInteger runCount = new AtomicInteger();
		Runnable runnable = () -> {
			log.info("Start scheduleWithFixedDelay:    " + LocalTime.now());
			log.info("task가 시작된 횟수 : " + runCount.incrementAndGet());

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			log.info("Finish scheduleWithFixedDelay:    " + LocalTime.now());
		};

		ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleWithFixedDelay(runnable, 0, 2, TimeUnit.SECONDS);

		// 0초에 task 실행-1 1초 실행 후 2초 delay
		// 3초에 task 실행-2 1초 실행 후 2초 delay
		// 6초에 task 실행-3 1초 실행 후 2초 delay
		Thread.sleep(7000L);
		executorService.shutdown();
	}



}
