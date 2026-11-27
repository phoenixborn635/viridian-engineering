package dev.viridianengineering;

/** Client-only constructor work; only loaded when running on a client. */
final class ClientInit {
    private ClientInit() {}

    static void init() {
        dev.viridianengineering.client.VEPartials.init();
        dev.viridianengineering.client.LanceRenderer.init();
    }
}
