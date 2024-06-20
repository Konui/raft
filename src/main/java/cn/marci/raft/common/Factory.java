package cn.marci.raft.common;

public abstract class Factory<T> {

    protected volatile T INSTANCE;

    public T getInstance() {
        if (INSTANCE == null) {
            synchronized (Factory.class) {
                if (INSTANCE == null) {
                    INSTANCE = createInstance();
                }
            }
        }
        return INSTANCE;
    }

    protected abstract T createInstance();

}
