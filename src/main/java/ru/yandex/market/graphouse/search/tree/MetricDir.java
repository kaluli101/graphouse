package ru.yandex.market.graphouse.search.tree;

import ru.yandex.market.graphouse.MetricUtil;
import ru.yandex.market.graphouse.retention.RetentionProvider;
import ru.yandex.market.graphouse.search.MetricStatus;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dmitry Andreev <a href="mailto:AndreevDm@yandex-team.ru"></a>
 * @date 25/01/2017
 */
public abstract class MetricDir extends MetricBase {

    private AtomicInteger visibleChildren = new AtomicInteger(-1);

    public MetricDir(MetricDir parent, String name, MetricStatus status) {
        super(parent, name, status);
    }

    @Override
    public boolean isDir() {
        return true;
    }

    @Override
    public String getName() {
        if (isRoot()) {
            return "ROOT";
        }

        final String dirName = name + MetricUtil.LEVEL_SPLITTER;
        return parent.isRoot() ? dirName : parent.toString() + dirName;
    }

    public abstract Map<String, MetricDir> getDirs();

    public abstract Map<String, MetricName> getMetrics();

    public abstract boolean hasDirs();

    public abstract boolean hasMetrics();

    public abstract int loadedMetricCount();

    public abstract int loadedDirCount();

    public abstract MetricDir maybeGetDir(String name);

    public abstract MetricName maybeGetMetric(String name);

    public MetricDir getOrCreateDir(String name, MetricStatus status,
                                    MetricDirFactory metricDirFactory, int maxSubDirsPerDir) {
        Map<String, MetricDir> dirs = getDirs();
        MetricDir dir = dirs.get(name);
        if (dir != null) {
            return dir;
        }
        if (maxSubDirsPerDir > 0 && dirs.size() >= maxSubDirsPerDir && !status.handmade()) {
            return null;
        }
        String internName = name.intern();
        dir = dirs.computeIfAbsent(
            internName,
            s -> metricDirFactory.createMetricDir(MetricDir.this, internName, status)
        );
        notifyChildStatusChange(dir, null); //Can be false call, but its ok
        return dir;
    }

    public MetricName getOrCreateMetric(String name, MetricStatus status,
                                        RetentionProvider retentionProvider, int maxMetricsPerDir) {
        Map<String, MetricName> metrics = getMetrics();
        MetricName metric = metrics.get(name);
        if (metric != null) {
            return metric;
        }
        if (maxMetricsPerDir > 0 && metrics.size() >= maxMetricsPerDir && !status.handmade()) {
            return null;
        }
        String internName = name.intern();
        metric = metrics.computeIfAbsent(
            internName,
            s -> new MetricName(this, internName, status, retentionProvider.getRetention(getName() + name))
        );
        notifyChildStatusChange(metric, null); //Can be false call, but its ok
        return metric;
    }


    /**
     * if all the metrics in the directory are hidden, then we try to hide it {@link MetricStatus#AUTO_HIDDEN}.
     * if there is at least one open metric for the directory, then we try to open it {@link MetricStatus#SIMPLE}.
     */
    public void notifyChildStatusChange(MetricBase metricBase, MetricStatus oldStatus) {
        if (isRoot()) {
            return;
        }

        MetricStatus newStatus = metricBase.getStatus();

        // remove from the tree, it does not make sense to store it
        if (newStatus == MetricStatus.AUTO_HIDDEN) {
            if (metricBase.isDir()) {
                getDirs().remove(metricBase.getName());
            } else {
                getMetrics().remove(metricBase.getName());
            }
        }

        if (oldStatus != null && oldStatus.visible() == newStatus.visible()) {
            return;
        }

        // if all the metrics in the directory are hidden, then we try to hide it {@link MetricStatus#AUTO_HIDDEN}
        // if there is at least one open metric for the directory, then we try to open it {@link MetricStatus#SIMPLE}
        initVisibleCounter();
        if (newStatus.visible()) {
            setStatus(MetricStatus.SIMPLE);
            visibleChildren.getAndIncrement();
        } else {
            visibleChildren.getAndUpdate(operand -> {
                int count = operand - 1;
                setStatus(count > 0 ? MetricStatus.SIMPLE : MetricStatus.AUTO_HIDDEN);
                return count;
            });
        }
    }

    private void initVisibleCounter() {
        visibleChildren.getAndUpdate(operand -> {
            if (operand >= 0) {
                return operand;
            }
            int count = 0;
            for (MetricDir metricDir : getDirs().values()) {
                if (metricDir.visible()) {
                    count++;
                }
            }

            for (MetricName metricName : getMetrics().values()) {
                if (metricName.visible()) {
                    count++;
                }
            }
            return count;
        });
    }

    @Override
    public String toString() {
        return getName();
    }
}
