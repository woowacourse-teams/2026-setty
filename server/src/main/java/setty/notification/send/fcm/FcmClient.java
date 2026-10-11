package setty.notification.send.fcm;

import com.google.firebase.messaging.Message;
import java.util.List;

public interface FcmClient {

    List<FcmDelivery> sendEach(List<Message> messages);
}
