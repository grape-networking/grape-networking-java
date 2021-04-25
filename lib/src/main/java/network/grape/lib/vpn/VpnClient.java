package network.grape.lib.vpn;

//import io.grpc.ManagedChannel;
//import io.grpc.ManagedChannelBuilder;
//import network.grape.vpn.VpnGrpc;
//import network.grape.vpn.VpnService;
//
//public class VpnClient {
//    public void connect() {
//        VpnService.HelloRequest helloRequest = VpnService.HelloRequest.newBuilder().setName("Test").build();
//        ManagedChannel mChannel = ManagedChannelBuilder.forAddress("www.jasonernst.com", 8888).usePlaintext().build();
//        VpnGrpc.VpnBlockingStub futureStub = VpnGrpc.newBlockingStub(mChannel);
//        VpnService.HelloResponse response = futureStub.sayHello(helloRequest);
//    }
//}
