import org.telegram.messenger.*;
import org.telegram.messenger.tj.TjAccountOrder;
import org.telegram.messenger.tj.TjMessageEditPolicy;
import java.util.*;
public class AccountPolicyTest {
    static int count;
    static void check(boolean ok) { count++; if(!ok) throw new AssertionError("check " + count); }
    public static void main(String[] args) {
        for(int i=0;i<3;i++) { UserConfig.getInstance(i).active=true; UserConfig.getInstance(i).owner=100+i; }
        check(TjAccountOrder.activeAccounts().equals(Arrays.asList(0,1,2)));
        TjAccountOrder.save(Arrays.asList(2,0,1));
        check(MessagesController.saved.equals("102,100,101"));
        check(NotificationCenter.notifications==3);
        check(TjAccountOrder.activeAccounts().equals(Arrays.asList(2,0,1)));
        ArrayList<Integer> subset=new ArrayList<>(Arrays.asList(1,2)); TjAccountOrder.sort(subset);
        check(subset.equals(Arrays.asList(2,1)));
        check(MessagesController.saved.equals("102,100,101"));
        UserConfig.getInstance(2).owner=200;
        check(TjAccountOrder.activeAccounts().equals(Arrays.asList(0,1,2)));
        UserConfig.getInstance(0).active=false;
        check(TjAccountOrder.activeAccounts().equals(Arrays.asList(1,2)));
        MessagesController.saved="200,garbage,200,101";
        check(TjAccountOrder.activeAccounts().equals(Arrays.asList(2,1)));
        check(TjMessageEditPolicy.usesBotEditWindow(true,100,true,0));
        check(TjMessageEditPolicy.usesBotEditWindow(true,100,false,100));
        check(!TjMessageEditPolicy.usesBotEditWindow(true,100,false,101));
        check(!TjMessageEditPolicy.usesBotEditWindow(false,100,true,100));
        check(!TjMessageEditPolicy.usesBotEditWindow(true,0,true,0));
        System.out.println(count+" account order/notification/bot edit policy checks passed");
    }
}
