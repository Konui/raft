package cn.marci.raft.serializer.hessian;

import cn.marci.raft.serializer.Serializer;
import cn.marci.raft.serializer.SerializerException;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
public class HessianSerializer implements Serializer {

    @Override
    public byte[] serialize(Object object) throws IOException {
        Hessian2Output ho = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ho = new Hessian2Output(baos);
            ho.writeObject(object);
            ho.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("hessian serialize error", e);
            throw new SerializerException("hessian serialize error");
        } finally {
            if (ho != null) {
                ho.close();
            }
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes) throws IOException {
        Hessian2Input input = null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            input = new Hessian2Input(in);
            return (T) input.readObject();
        } catch (Exception e) {
            log.error("hessian deserialize error", e);
            throw new SerializerException("hessian deserialize error");
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }
}
