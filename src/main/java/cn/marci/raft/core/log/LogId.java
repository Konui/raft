package cn.marci.raft.core.log;

import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Data
public class LogId implements Comparable<LogId>, Serializable {

    private long term;

    private long index;

    public LogId() {
        this(0, 0);
    }

    public LogId(long term, long index) {
        this.term = term;
        this.index = index;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof LogId logId)) return false;
        return term == logId.term && index == logId.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, index);
    }

    @Override
    public int compareTo(final LogId o) {
        // Compare term at first
        final int c = Long.compare(term, term);
        if (c == 0) {
            return Long.compare(index, index);
        } else {
            return c;
        }
    }
}
