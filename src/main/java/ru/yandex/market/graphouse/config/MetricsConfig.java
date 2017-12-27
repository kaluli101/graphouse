package ru.yandex.market.graphouse.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.yandex.market.graphouse.MetricValidator;
import ru.yandex.market.graphouse.cacher.MetricCacher;
import ru.yandex.market.graphouse.data.MetricDataService;
import ru.yandex.market.graphouse.monitoring.Monitoring;
import ru.yandex.market.graphouse.retention.ClickHouseRetentionProvider;
import ru.yandex.market.graphouse.retention.DefaultRetentionProvider;
import ru.yandex.market.graphouse.retention.RetentionProvider;
import ru.yandex.market.graphouse.search.MetricSearch;
import ru.yandex.market.graphouse.server.MetricFactory;
import ru.yandex.market.graphouse.statistics.StatisticsService;

/**
 * @author Vlad Vinogradov <a href="mailto:vladvin@yandex-team.ru"></a>
 * @date 10.11.16
 */
@Configuration
@Import({StatisticsConfig.class})
public class MetricsConfig {

    @Autowired
    private Monitoring monitoring;

    @Autowired
    private JdbcTemplate clickHouseJdbcTemplate;

    @Autowired
    private StatisticsService statisticsService;

    @Value("${graphouse.clickhouse.data-table}")
    private String graphiteDataTable;

    @Value("${graphouse.clickhouse.retention-config}")
    private String retentionConfig;

    @Value("${graphouse.metric-validation.min-length}")
    private int minMetricLength;

    @Value("${graphouse.metric-validation.max-length}")
    private int maxMetricLength;

    @Value("${graphouse.metric-validation.min-levels}")
    private int minDots;

    @Value("${graphouse.metric-validation.max-levels}")
    private int maxDots;

    @Value("${graphouse.metric-validation.regexp}")
    private String metricRegexp;

    @Bean
    public MetricSearch metricSearch() {
        return new MetricSearch(
            clickHouseJdbcTemplate, monitoring,
            metricValidator(), retentionProvider(), statisticsService
        );
    }

    @Bean
    public RetentionProvider retentionProvider() {
        if (retentionConfig.isEmpty()) {
            return new DefaultRetentionProvider();
        } else {
            return new ClickHouseRetentionProvider(clickHouseJdbcTemplate, retentionConfig);
        }
    }

    @Bean
    public MetricDataService metricDataService() {
        return new MetricDataService(metricSearch(), clickHouseJdbcTemplate, graphiteDataTable);
    }

    @Bean
    public MetricValidator metricValidator() {
        return new MetricValidator(metricRegexp, minMetricLength, maxMetricLength, minDots, maxDots);
    }

    @Bean
    public MetricCacher metricCacher() {
        return new MetricCacher(clickHouseJdbcTemplate, monitoring, statisticsService);
    }

    @Bean
    public MetricFactory metricFactory() {
        return new MetricFactory(metricSearch(), metricValidator());
    }
}
