package cn.marci.raft.core.rpc.processor;

import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeManager;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.rpc.UserProcessor;

import java.util.function.Consumer;

public class AppendEntriesProcessor implements UserProcessor<AppendEntriesRequest> {
    @Override
    public Object handleRequest(AppendEntriesRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void handleRequestAsync(AppendEntriesRequest request, Consumer<Object> sendCallback) {
        Node node = NodeManager.getInstance().get(request.getGroup(), request.getToEndpoint());
        AppendEntriesResponse appendEntriesResponse = node.handleAppendEntries(request, sendCallback);
        if (appendEntriesResponse != null) {
            sendCallback.accept(appendEntriesResponse);
        }
    }

    @Override
    public String interest() {
        return AppendEntriesRequest.class.getName();
    }

    @Override
    public HandlerType handlerType() {
        return HandlerType.ASYNC_SEND_RESP_BY_USER;
    }
}
