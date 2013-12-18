import java.util.*;

import sparta.checkers.quals.*;
import sparta.checkers.quals.ParameterizedFlowPermission;
import static sparta.checkers.quals.FlowPermission.*;


class MyList<T extends Object> {
}

class NewList {
    List<@Source(ACCESS_FINE_LOCATION) Object> good1  = new ArrayList<@Source(ACCESS_FINE_LOCATION) Object>();
    //:: error: (assignment.type.incompatible)
    List<@Source(ACCESS_FINE_LOCATION) Object> bad1 = new ArrayList<@Source(ANY) Object>();

    MyList</*@Source(FlowPermission.ACCESS_FINE_LOCATION)*/ Object> good2  = new MyList<@Source(ACCESS_FINE_LOCATION) Object>();
    //:: error: (assignment.type.incompatible)
    MyList<@Source(ACCESS_FINE_LOCATION) Object> bad2 = new MyList<@Source(FlowPermission.ANY) Object>();
}
