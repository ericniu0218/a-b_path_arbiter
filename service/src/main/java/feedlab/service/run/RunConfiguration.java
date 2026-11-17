package feedlab.service.run;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class RunConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** One run at a time: a simulation is CPU-bound and holds histograms in memory. */
    @Bean
    TaskExecutor runTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("run-");
        return executor;
    }
}
