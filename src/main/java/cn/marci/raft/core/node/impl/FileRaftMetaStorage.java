package cn.marci.raft.core.node.impl;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.node.RaftMetaStorage;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileRaftMetaStorage implements RaftMetaStorage {

    private final String filePath;

    private final Path path;

    private volatile long term;

    private volatile Endpoint votedFor;

    public FileRaftMetaStorage(String filePath) {
        this.filePath = filePath;
        try {
            this.path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            load();
        } catch (Exception e) {
            throw new IllegalStateException("create raft meta storage path failed", e);
        }
    }

    private void load() {
        try {
            this.term = 0;
            this.votedFor = null;
            if (!Files.exists(path)) {
                return;
            }
            List<String> lines = Files.readAllLines(path);
            if (lines.size() > 0) {
                this.term = Long.parseLong(lines.get(0));
            }
            if (lines.size() > 1) {
                this.votedFor = new Endpoint(lines.get(1));
            }
        } catch (Exception e) {
            throw new IllegalStateException("load raft meta storage failed", e);
        }
    }

    private boolean save() {
        List<String> lines = new ArrayList<>(2);
        lines.add(String.valueOf(term));
        if (votedFor != null) {
            lines.add(votedFor.toString().substring(1, votedFor.toString().length() - 1));
        }
        try {
            Files.write(path, lines);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("save raft meta storage failed", e);
        }
    }

    @Override
    public boolean setTerm(long term) {
        this.term = term;
        return save();
    }

    @Override
    public long getTerm() {
        return this.term;
    }

    @Override
    public boolean setVotedFor(Endpoint endpoint) {
        this.votedFor = endpoint;
        return save();
    }

    @Override
    public Endpoint getVotedFor() {
        return this.votedFor;
    }

    @Override
    public boolean setTermAndVotedFor(long term, Endpoint endpoint) {
        this.term = term;
        this.votedFor = endpoint;
        return save();
    }
}
