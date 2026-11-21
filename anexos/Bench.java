import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public class Bench {

    static final int N_PRODUCTS = 1000, N_CUSTOMERS = 500, ORDER_SLOTS = 50_000, STOCK0 = 2_000_000;
    static final int WORK = 40;
    static final double TARGET_SECS = 20.0;

    static double priceOf(int p) { return 5.0 + ((p * 37) % 500) / 10.0; }

    static long pricingCents(double price, int qty, int tier) {
        double v = price * qty;
        for (int i = 0; i < WORK; i++) {
            v = v * (1.0 + 0.0001 * ((i + tier) & 3)) - 0.00001 * i + Math.sqrt(v + i) * 1e-6;
        }
        return Math.round(v * 100.0);
    }

    interface Shop {
        long viewProduct(int productId);
        long placeOrder(int customerId, int productId, int qty);
        long customerSummary(int customerId);
        long checksum();
    }

    static Shop create(String arch) {
        return switch (arch) {
            case "trad" -> new TraditionalShop();
            case "mod" -> new ModularShop();
            default -> throw new IllegalArgumentException("arquitectura: " + arch);
        };
    }

    static final class TraditionalShop implements Shop {
        static final class Product {
            final double price; final AtomicInteger stock = new AtomicInteger(STOCK0);
            Product(double price) { this.price = price; }
        }
        static final class Customer {
            final int tier; final AtomicLong spent = new AtomicLong();
            Customer(int tier) { this.tier = tier; }
        }
        static final class Order {
            final long id, cents; final int customerId, productId, qty;
            Order(long id, int c, int p, int q, long cents) { this.id = id; customerId = c; productId = p; qty = q; this.cents = cents; }
        }
        static final class Payment {
            final long orderId, cents;
            Payment(long orderId, long cents) { this.orderId = orderId; this.cents = cents; }
        }

        private final Product[] products = new Product[N_PRODUCTS];
        private final Customer[] customers = new Customer[N_CUSTOMERS];
        private final AtomicReferenceArray<Order> orders = new AtomicReferenceArray<>(ORDER_SLOTS);
        private final AtomicReferenceArray<Payment> payments = new AtomicReferenceArray<>(ORDER_SLOTS);
        private final AtomicLong seq = new AtomicLong();

        TraditionalShop() {
            for (int i = 0; i < N_PRODUCTS; i++) products[i] = new Product(priceOf(i));
            for (int i = 0; i < N_CUSTOMERS; i++) customers[i] = new Customer(i % 3);
        }

        public long viewProduct(int p) {
            Product pr = products[p];
            return pricingCents(pr.price, 1, 0) ^ pr.stock.get();
        }

        public long placeOrder(int c, int p, int q) {
            Product pr = products[p];
            Customer cu = customers[c];
            if (pr.stock.addAndGet(-q) < 0) { pr.stock.addAndGet(q); return -1; }
            long cents = pricingCents(pr.price, q, cu.tier);
            long id = seq.incrementAndGet();
            orders.set((int) (id % ORDER_SLOTS), new Order(id, c, p, q, cents));
            payments.set((int) (id % ORDER_SLOTS), new Payment(id, cents));
            cu.spent.addAndGet(cents);
            return id * 31 + cents;
        }

        public long customerSummary(int c) {
            Customer cu = customers[c];
            Product pr = products[c % N_PRODUCTS];
            return cu.spent.get() + cu.tier * 7L + Math.round(pr.price * 100);
        }

        public long checksum() {
            long s = 0;
            for (Customer cu : customers) s += cu.spent.get();
            for (Product pr : products) s += pr.stock.get();
            return s;
        }
    }

    record OrderPlaced(long orderId, int customerId, long cents) {}
    record PaymentRegistered(long orderId, int customerId, long cents) {}

    static final class EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();
        <T> void on(Class<T> type, Consumer<T> l) {
            listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(o -> l.accept(type.cast(o)));
        }
        void publish(Object e) {
            List<Consumer<Object>> ls = listeners.get(e.getClass());
            if (ls != null) for (Consumer<Object> l : ls) l.accept(e);
        }
    }

    record ProductInfo(int id, String name, double price) {}
    interface CatalogApi { ProductInfo find(int id); }
    static final class CatalogModule implements CatalogApi {
        private record ProductData(int id, String name, double price) {}
        private final ProductData[] data = new ProductData[N_PRODUCTS];
        CatalogModule() { for (int i = 0; i < N_PRODUCTS; i++) data[i] = new ProductData(i, "P" + i, priceOf(i)); }
        public ProductInfo find(int id) { ProductData d = data[id]; return new ProductInfo(d.id(), d.name(), d.price()); }
    }

    interface InventoryApi { boolean reserve(int productId, int qty); int available(int productId); }
    static final class InventoryModule implements InventoryApi {
        private final AtomicIntegerArray stock = new AtomicIntegerArray(N_PRODUCTS);
        InventoryModule() { for (int i = 0; i < N_PRODUCTS; i++) stock.set(i, STOCK0); }
        public boolean reserve(int p, int q) {
            if (stock.addAndGet(p, -q) < 0) { stock.addAndGet(p, q); return false; }
            return true;
        }
        public int available(int p) { return stock.get(p); }
        long totalStock() { long s = 0; for (int i = 0; i < N_PRODUCTS; i++) s += stock.get(i); return s; }
    }

    record CustomerInfo(int id, int tier, long spentCents) {}
    interface CustomerApi { CustomerInfo find(int id); }
    static final class CustomersModule implements CustomerApi {
        private final AtomicLongArray spent = new AtomicLongArray(N_CUSTOMERS);
        CustomersModule(EventBus bus) { bus.on(PaymentRegistered.class, e -> spent.addAndGet(e.customerId(), e.cents())); }
        public CustomerInfo find(int id) { return new CustomerInfo(id, id % 3, spent.get(id)); }
        long totalSpent() { long s = 0; for (int i = 0; i < N_CUSTOMERS; i++) s += spent.get(i); return s; }
    }

    static final class PaymentsModule {
        private record PaymentData(long orderId, long cents) {}
        private final AtomicReferenceArray<PaymentData> slots = new AtomicReferenceArray<>(ORDER_SLOTS);
        PaymentsModule(EventBus bus) {
            bus.on(OrderPlaced.class, e -> {
                slots.set((int) (e.orderId() % ORDER_SLOTS), new PaymentData(e.orderId(), e.cents()));
                bus.publish(new PaymentRegistered(e.orderId(), e.customerId(), e.cents()));
            });
        }
    }

    interface OrderApi { long place(int customerId, int productId, int qty); }
    static final class OrdersModule implements OrderApi {
        private record OrderData(long id, int customerId, int productId, int qty, long cents) {}
        private final AtomicReferenceArray<OrderData> slots = new AtomicReferenceArray<>(ORDER_SLOTS);
        private final AtomicLong seq = new AtomicLong();
        private final CatalogApi catalog; private final InventoryApi inventory;
        private final CustomerApi customers; private final EventBus bus;
        OrdersModule(CatalogApi catalog, InventoryApi inventory, CustomerApi customers, EventBus bus) {
            this.catalog = catalog; this.inventory = inventory; this.customers = customers; this.bus = bus;
        }
        public long place(int c, int p, int q) {
            ProductInfo pi = catalog.find(p);
            CustomerInfo ci = customers.find(c);
            if (!inventory.reserve(p, q)) return -1;
            long cents = pricingCents(pi.price(), q, ci.tier());
            long id = seq.incrementAndGet();
            slots.set((int) (id % ORDER_SLOTS), new OrderData(id, c, p, q, cents));
            bus.publish(new OrderPlaced(id, c, cents));
            return id * 31 + cents;
        }
    }

    static final class ModularShop implements Shop {
        private final CatalogApi catalog; private final InventoryApi inventory;
        private final CustomerApi customers; private final OrderApi orders;
        private final InventoryModule invModule; private final CustomersModule cusModule;

        ModularShop() {
            EventBus bus = new EventBus();
            CatalogModule cat = new CatalogModule();
            InventoryModule inv = new InventoryModule();
            CustomersModule cus = new CustomersModule(bus);
            new PaymentsModule(bus);
            OrdersModule ord = new OrdersModule(cat, inv, cus, bus);
            catalog = cat; inventory = inv; customers = cus; orders = ord;
            invModule = inv; cusModule = cus;
        }

        public long viewProduct(int p) {
            ProductInfo pi = catalog.find(p);
            return pricingCents(pi.price(), 1, 0) ^ inventory.available(p);
        }
        public long placeOrder(int c, int p, int q) { return orders.place(c, p, q); }
        public long customerSummary(int c) {
            CustomerInfo ci = customers.find(c);
            ProductInfo pi = catalog.find(c % N_PRODUCTS);
            return ci.spentCents() + ci.tier() * 7L + Math.round(pi.price() * 100);
        }
        public long checksum() { return cusModule.totalSpent() + invModule.totalStock(); }
    }

    static long runOps(Shop shop, long ops, long seed) {
        long x = seed * 0x9E3779B97F4A7C15L + 0x1234567L;
        if (x == 0) x = 1;
        long acc = 0;
        for (long i = 0; i < ops; i++) {
            x ^= x << 13; x ^= x >>> 7; x ^= x << 17;
            int k = (int) ((x >>> 8) % 100);
            int p = (int) ((x >>> 20) % N_PRODUCTS);
            int c = (int) ((x >>> 40) % N_CUSTOMERS);
            if (k < 70) acc += shop.viewProduct(p);
            else if (k < 90) acc += shop.placeOrder(c, p, 1 + (int) ((x >>> 33) & 1));
            else acc += shop.customerSummary(c);
        }
        return acc;
    }

    static void runParallel(Shop shop, int threads, long ops, long seedBase, AtomicLong sink) throws InterruptedException {
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final long seed = seedBase + t;
            ts[t] = new Thread(() -> sink.addAndGet(runOps(shop, ops, seed)));
            ts[t].start();
        }
        for (Thread t : ts) t.join();
    }

    record Snap(long pkg, long dram, long nanos) {}

    static String read(Path p) throws IOException { return Files.readString(p).trim(); }

    static final class Rapl {
        final Path pkgDir; final Path dramDir; final long maxPkg; final long maxDram;
        Rapl() throws IOException {
            pkgDir = Path.of(System.getProperty("rapl.root", "/sys/class/powercap/intel-rapl:0"));
            Path d = null;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(pkgDir, "intel-rapl:0:*")) {
                for (Path p : ds) if (read(p.resolve("name")).equals("dram")) d = p;
            }
            dramDir = d;
            maxPkg = Long.parseLong(read(pkgDir.resolve("max_energy_range_uj")));
            maxDram = (d == null) ? 0 : Long.parseLong(read(d.resolve("max_energy_range_uj")));
        }
        Snap snap() throws IOException {
            return new Snap(Long.parseLong(read(pkgDir.resolve("energy_uj"))),
                            dramDir == null ? 0 : Long.parseLong(read(dramDir.resolve("energy_uj"))),
                            System.nanoTime());
        }
        static long wrap(long a, long b, long max) { return b >= a ? b - a : b + max - a; }
        double pkgJ(Snap a, Snap b) { return wrap(a.pkg(), b.pkg(), maxPkg) / 1e6; }
        double dramJ(Snap a, Snap b) { return dramDir == null ? 0 : wrap(a.dram(), b.dram(), maxDram) / 1e6; }
    }

    static int tempC() {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of("/sys/class/thermal"), "thermal_zone*")) {
            for (Path z : ds) {
                if (read(z.resolve("type")).equals("x86_pkg_temp")) return Integer.parseInt(read(z.resolve("temp"))) / 1000;
            }
        } catch (Exception e) {  }
        return -1;
    }

    static void verify() {
        String[] names = {"trad", "mod"};
        long[] res = new long[2], st = new long[2];
        for (int i = 0; i < 2; i++) {
            Shop s = create(names[i]);
            res[i] = runOps(s, 300_000, 7);
            st[i] = s.checksum();
        }
        boolean ok = res[0] == res[1] && st[0] == st[1];
        System.out.println(ok ? "EQUIVALENCIA FUNCIONAL OK (resultado=" + res[0] + ", estado=" + st[0] + ")"
                : "DIFERENCIA ENTRE ARQUITECTURAS: " + Arrays.toString(res) + " " + Arrays.toString(st));
        System.exit(ok ? 0 : 1);
    }

    static void calibrate() {
        double min = Double.MAX_VALUE;
        for (String arch : new String[]{"trad", "mod"}) {
            Shop s = create(arch);
            long t0 = System.nanoTime();
            while (System.nanoTime() - t0 < 4_000_000_000L) runOps(s, 100_000, 1);
            long ops = 0; t0 = System.nanoTime();
            while (System.nanoTime() - t0 < 4_000_000_000L) { runOps(s, 100_000, 2); ops += 100_000; }
            double rate = ops / ((System.nanoTime() - t0) / 1e9);
            System.err.printf(Locale.ROOT, "  %s: %.0f ops/s (1 hilo)%n", arch, rate);
            min = Math.min(min, rate);
        }
        System.out.println((long) (min * TARGET_SECS));
    }

    static void idle(int block, int secs) throws Exception {
        Rapl r = new Rapl();
        Snap a = r.snap();
        Thread.sleep(secs * 1000L);
        Snap b = r.snap();
        System.out.printf(Locale.ROOT, "%d,idle,-,0,0,%.4f,%.6f,%.6f,%d,0%n",
                block, (b.nanos() - a.nanos()) / 1e9, r.pkgJ(a, b), r.dramJ(a, b), tempC());
    }

    static void run(String arch, String load, int block, long opsPerThread) throws Exception {
        int threads = load.equals("pesada") ? 2 : 1;
        Rapl rapl = new Rapl();
        Shop shop = create(arch);
        AtomicLong sink = new AtomicLong();
        runParallel(shop, threads, (long) (opsPerThread * 0.4), 100, sink);
        Snap s0 = rapl.snap();
        runParallel(shop, threads, opsPerThread, 200, sink);
        Snap s1 = rapl.snap();
        System.out.printf(Locale.ROOT, "%d,%s,%s,%d,%d,%.4f,%.6f,%.6f,%d,%d%n",
                block, arch, load, threads, opsPerThread * threads, (s1.nanos() - s0.nanos()) / 1e9,
                rapl.pkgJ(s0, s1), rapl.dramJ(s0, s1), tempC(), sink.get() ^ shop.checksum());
    }

    public static void main(String[] a) throws Exception {
        try {
            switch (a[0]) {
                case "verify" -> verify();
                case "cal" -> calibrate();
                case "idle" -> idle(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                case "run" -> run(a[1], a[2], Integer.parseInt(a[3]), Long.parseLong(a[4]));
                default -> { System.err.println("modo desconocido: " + a[0]); System.exit(1); }
            }
        } catch (AccessDeniedException e) {
            System.err.println("Sin permiso para leer RAPL. Ejecuta: sudo chmod a+r /sys/class/powercap/intel-rapl:0*/energy_uj");
            System.exit(2);
        }
    }
}
