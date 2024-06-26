package cn.marci.raft.common;

public abstract class SingleFactory<T> {

    protected volatile T INSTANCE;

    public T getInstance() {
        if (INSTANCE == null) {
            synchronized (SingleFactory.class) {
                if (INSTANCE == null) {
                    INSTANCE = createInstance();
                }
            }
        }
        return INSTANCE;
    }

    protected abstract T createInstance();

}
