import { Feather } from "@expo/vector-icons";
import * as SecureStore from "expo-secure-store";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import useConnectionDetail from "../src/hooks/useConnectionDetail";
import {
  getContextMetaLabel,
  getContextSubtitle,
  getContextTitle,
} from "../src/services/connectionService";
import { createOrder, getLatestOrder, getOrdersByChat } from "../src/services/orderService";

const emptyForm = {
  quantity: "",
  unit: "",
  price: "",
  notes: "",
  contactName: "",
  contactPhone: "",
  addressLine1: "",
  addressLine2: "",
  village: "",
  city: "",
  state: "",
  country: "India",
  pincode: "",
  landmark: "",
  preferredDeliveryDate: "",
  preferredTimeSlot: "",
  instructions: "",
};

const toInputText = (value) => {
  if (value === undefined || value === null || value === "") return "";
  return String(value);
};

const formatMoney = (amount, currency) => {
  if (amount === undefined || amount === null || Number.isNaN(Number(amount))) return "-";
  return `${String(currency || "INR").toUpperCase()} ${Number(amount)}`;
};

const normalizeDateString = (value) => {
  const trimmed = String(value || "").trim();
  if (!trimmed) return "";

  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) {
    throw new Error("Preferred delivery date must be in YYYY-MM-DD format.");
  }
  return trimmed;
};

const parsePositiveNumber = (value, fieldName) => {
  const parsed = Number(String(value || "").trim());
  if (!parsed || Number.isNaN(parsed) || parsed <= 0) {
    throw new Error(`${fieldName} must be greater than 0.`);
  }
  return parsed;
};

export default function CreateOrderScreen() {
  const router = useRouter();
  const params = useLocalSearchParams();
  const [token, setToken] = useState("");
  const [authLoading, setAuthLoading] = useState(true);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [latestOrder, setLatestOrder] = useState(null);
  const [ordersError, setOrdersError] = useState("");

  const connectionId = params?.connectionId ? String(params.connectionId) : "";
  const chatId = params?.chatId ? String(params.chatId) : "";

  useEffect(() => {
    let mounted = true;

    const hydrateAuth = async () => {
      try {
        setAuthLoading(true);
        const storedToken = await SecureStore.getItemAsync("auth_token");
        if (!mounted) return;
        setToken(storedToken ? String(storedToken) : "");
      } finally {
        if (mounted) {
          setAuthLoading(false);
        }
      }
    };

    hydrateAuth();

    return () => {
      mounted = false;
    };
  }, []);

  const {
    data: connection,
    loading,
    refreshing,
    error,
    refresh,
  } = useConnectionDetail(connectionId, token, !authLoading && !!token && !!connectionId);

  const cropContext = useMemo(() => {
    const contexts = Array.isArray(connection?.contexts) ? connection.contexts : [];
    const cropContexts = contexts.filter((context) => String(context?.type || "").toUpperCase() === "CROP");
    return cropContexts[cropContexts.length - 1] || contexts[contexts.length - 1] || null;
  }, [connection]);

  useEffect(() => {
    if (!cropContext) return;

    setForm((current) => ({
      ...current,
      unit: current.unit || cropContext.unit || "",
      price: current.price || toInputText(cropContext.price),
    }));
  }, [cropContext]);

  const loadChatOrders = useCallback(async () => {
    if (!token || !chatId) {
      setLatestOrder(null);
      return;
    }

    try {
      setOrdersLoading(true);
      setOrdersError("");
      const orders = await getOrdersByChat(chatId, token);
      setLatestOrder(getLatestOrder(orders));
    } catch (loadError) {
      setOrdersError(loadError?.message || "Failed to load chat orders.");
    } finally {
      setOrdersLoading(false);
    }
  }, [chatId, token]);

  useEffect(() => {
    if (!authLoading && token && chatId) {
      loadChatOrders();
    }
  }, [authLoading, token, chatId, loadChatOrders]);

  const handleChange = useCallback((key, value) => {
    setForm((current) => ({
      ...current,
      [key]: value,
    }));
  }, []);

  const handleSubmit = useCallback(async () => {
    if (!connectionId || !chatId || !cropContext?.refId) {
      Alert.alert("Order", "Chat connection details are missing.");
      return;
    }

    try {
      setSubmitting(true);

      const quantity = parsePositiveNumber(form.quantity, "Quantity");
      const price = parsePositiveNumber(form.price, "Price");
      const preferredDeliveryDate = form.preferredDeliveryDate
        ? normalizeDateString(form.preferredDeliveryDate)
        : undefined;

      const order = await createOrder(
        {
          connectionId,
          chatId,
          cropListingId: cropContext.refId,
          quantity,
          unit: form.unit || cropContext.unit || undefined,
          agreedPrice: price,
          currency: "INR",
          notes: form.notes || undefined,
          deliveryDetails: {
            contactName: form.contactName,
            contactPhone: form.contactPhone,
            addressLine1: form.addressLine1,
            addressLine2: form.addressLine2 || undefined,
            village: form.village || undefined,
            city: form.city,
            state: form.state,
            country: form.country,
            pincode: form.pincode || undefined,
            landmark: form.landmark || undefined,
            preferredDeliveryDate: preferredDeliveryDate || undefined,
            preferredTimeSlot: form.preferredTimeSlot || undefined,
            instructions: form.instructions || undefined,
          },
        },
        token
      );

      setLatestOrder(order);
      Alert.alert(
        "Order Created",
        `${order?.orderCode || "Order"} has been created. Payment will be the next step.`,
        [
          {
            text: "Back to Chat",
            onPress: () =>
              router.replace(`/(buyer)/chat/${chatId}?connectionId=${connectionId}`),
          },
        ]
      );
    } catch (submitError) {
      Alert.alert("Order", submitError?.message || "Failed to create order.");
    } finally {
      setSubmitting(false);
    }
  }, [chatId, connectionId, cropContext, form, router, token]);

  const handleRefresh = useCallback(async () => {
    await Promise.all([refresh(), loadChatOrders()]);
  }, [loadChatOrders, refresh]);

  if (authLoading || (loading && !refreshing)) {
    return (
      <SafeAreaView style={styles.centerScreen}>
        <ActivityIndicator size="large" color="#16a34a" />
      </SafeAreaView>
    );
  }

  if (!token) {
    return (
      <SafeAreaView style={styles.centerScreen}>
        <Text style={styles.emptyText}>Authentication required.</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <View style={styles.header}>
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()} activeOpacity={0.85}>
            <Feather name="arrow-left" size={18} color="#111827" />
          </TouchableOpacity>
          <View style={styles.headerTextWrap}>
            <Text style={styles.headerTitle}>Create Order</Text>
            <Text style={styles.headerSubtitle}>Set quantity, Price, and delivery details</Text>
          </View>
        </View>

        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          refreshControl={
            <RefreshControl
              refreshing={refreshing || ordersLoading}
              onRefresh={handleRefresh}
              tintColor="#16a34a"
              colors={["#16a34a"]}
            />
          }
        >
          {!!error && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          {!!ordersError && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{ordersError}</Text>
            </View>
          )}

          <View style={styles.sectionCard}>
            <Text style={styles.sectionTitle}>Crop Details</Text>
            {cropContext ? (
              <View style={styles.contextCard}>
                <Text style={styles.contextTitle}>{getContextTitle(cropContext)}</Text>
                {!!getContextSubtitle(cropContext) && (
                  <Text style={styles.contextSubtitle}>{getContextSubtitle(cropContext)}</Text>
                )}
                <Text style={styles.contextMeta}>{getContextMetaLabel(cropContext)}</Text>
              </View>
            ) : (
              <Text style={styles.emptyText}>Crop context is not available for this chat.</Text>
            )}
          </View>

          {!!latestOrder && (
            <View style={styles.sectionCard}>
              <Text style={styles.sectionTitle}>Latest Order In This Chat</Text>
              <View style={styles.summaryCard}>
                <Text style={styles.summaryTitle}>{latestOrder.orderCode || "Order"}</Text>
                <Text style={styles.summaryLine}>Status: {latestOrder.status}</Text>
                <Text style={styles.summaryLine}>Payment: {latestOrder.paymentStatus}</Text>
                <Text style={styles.summaryLine}>
                  Total: {formatMoney(latestOrder.totalAmount, latestOrder.currency)}
                </Text>
              </View>
            </View>
          )}

          <View style={styles.sectionCard}>
            <Text style={styles.sectionTitle}>Order Details</Text>
            <View style={styles.row}>
              <View style={styles.flexField}>
                <Text style={styles.label}>Quantity</Text>
                <TextInput
                  value={form.quantity}
                  onChangeText={(value) => handleChange("quantity", value)}
                  placeholder="Enter quantity"
                  keyboardType="decimal-pad"
                  style={styles.input}
                />
              </View>
              <View style={styles.smallField}>
                <Text style={styles.label}>Unit</Text>
                <TextInput
                  value={form.unit}
                  onChangeText={(value) => handleChange("unit", value)}
                  placeholder="kg"
                  autoCapitalize="none"
                  style={styles.input}
                />
              </View>
            </View>

            <Text style={styles.label}>Price</Text>
            <TextInput
              value={form.price}
              onChangeText={(value) => handleChange("price", value)}
              placeholder="Enter final amount"
              keyboardType="decimal-pad"
              style={styles.input}
            />

            <Text style={styles.label}>Notes</Text>
            <TextInput
              value={form.notes}
              onChangeText={(value) => handleChange("notes", value)}
              placeholder="Any special request for the farmer"
              multiline
              style={[styles.input, styles.textArea]}
            />
          </View>

          <View style={styles.sectionCard}>
            <Text style={styles.sectionTitle}>Delivery Details</Text>
            <Text style={styles.label}>Contact Name</Text>
            <TextInput
              value={form.contactName}
              onChangeText={(value) => handleChange("contactName", value)}
              placeholder="Receiver name"
              style={styles.input}
            />

            <Text style={styles.label}>Contact Phone</Text>
            <TextInput
              value={form.contactPhone}
              onChangeText={(value) => handleChange("contactPhone", value)}
              placeholder="Phone number"
              keyboardType="phone-pad"
              style={styles.input}
            />

            <Text style={styles.label}>Address Line 1</Text>
            <TextInput
              value={form.addressLine1}
              onChangeText={(value) => handleChange("addressLine1", value)}
              placeholder="Primary address"
              style={styles.input}
            />

            <Text style={styles.label}>Address Line 2</Text>
            <TextInput
              value={form.addressLine2}
              onChangeText={(value) => handleChange("addressLine2", value)}
              placeholder="Apartment, area, etc."
              style={styles.input}
            />

            <Text style={styles.label}>Village</Text>
            <TextInput
              value={form.village}
              onChangeText={(value) => handleChange("village", value)}
              placeholder="Village or locality"
              style={styles.input}
            />

            <View style={styles.row}>
              <View style={styles.flexField}>
                <Text style={styles.label}>City</Text>
                <TextInput
                  value={form.city}
                  onChangeText={(value) => handleChange("city", value)}
                  placeholder="City"
                  style={styles.input}
                />
              </View>
              <View style={styles.flexField}>
                <Text style={styles.label}>State</Text>
                <TextInput
                  value={form.state}
                  onChangeText={(value) => handleChange("state", value)}
                  placeholder="State"
                  style={styles.input}
                />
              </View>
            </View>

            <View style={styles.row}>
              <View style={styles.flexField}>
                <Text style={styles.label}>Country</Text>
                <TextInput
                  value={form.country}
                  onChangeText={(value) => handleChange("country", value)}
                  placeholder="Country"
                  style={styles.input}
                />
              </View>
              <View style={styles.flexField}>
                <Text style={styles.label}>Pincode</Text>
                <TextInput
                  value={form.pincode}
                  onChangeText={(value) => handleChange("pincode", value)}
                  placeholder="Pincode"
                  keyboardType="number-pad"
                  style={styles.input}
                />
              </View>
            </View>

            <Text style={styles.label}>Landmark</Text>
            <TextInput
              value={form.landmark}
              onChangeText={(value) => handleChange("landmark", value)}
              placeholder="Nearby landmark"
              style={styles.input}
            />

            <View style={styles.row}>
              <View style={styles.flexField}>
                <Text style={styles.label}>Preferred Delivery Date</Text>
                <TextInput
                  value={form.preferredDeliveryDate}
                  onChangeText={(value) => handleChange("preferredDeliveryDate", value)}
                  placeholder="YYYY-MM-DD"
                  autoCapitalize="none"
                  style={styles.input}
                />
              </View>
              <View style={styles.flexField}>
                <Text style={styles.label}>Preferred Time Slot</Text>
                <TextInput
                  value={form.preferredTimeSlot}
                  onChangeText={(value) => handleChange("preferredTimeSlot", value)}
                  placeholder="10 AM - 1 PM"
                  style={styles.input}
                />
              </View>
            </View>

            <Text style={styles.label}>Instructions</Text>
            <TextInput
              value={form.instructions}
              onChangeText={(value) => handleChange("instructions", value)}
              placeholder="Delivery notes"
              multiline
              style={[styles.input, styles.textArea]}
            />
          </View>

          <View style={styles.actionWrap}>
            <TouchableOpacity
              style={[styles.primaryBtn, submitting && styles.primaryBtnDisabled]}
              activeOpacity={0.85}
              onPress={handleSubmit}
              disabled={submitting || !cropContext}
            >
              {submitting ? (
                <ActivityIndicator size="small" color="#ffffff" />
              ) : (
                <Text style={styles.primaryBtnText}>Create Order</Text>
              )}
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.secondaryBtn}
              activeOpacity={0.85}
              onPress={() => router.back()}
            >
              <Text style={styles.secondaryBtnText}>Back to Chat</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f3f9f4",
  },
  centerScreen: {
    flex: 1,
    backgroundColor: "#f3f9f4",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 10,
    backgroundColor: "#f3f9f4",
  },
  backBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: "#ffffff",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: "#e5e7eb",
    marginRight: 10,
  },
  headerTextWrap: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: "800",
    color: "#111827",
  },
  headerSubtitle: {
    marginTop: 2,
    fontSize: 12,
    fontWeight: "500",
    color: "#6b7280",
  },
  content: {
    padding: 16,
    paddingBottom: 36,
  },
  sectionCard: {
    backgroundColor: "#ffffff",
    borderRadius: 18,
    borderWidth: 1,
    borderColor: "#e5e7eb",
    padding: 16,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "800",
    color: "#111827",
    marginBottom: 12,
  },
  contextCard: {
    backgroundColor: "#f9fafb",
    borderRadius: 12,
    padding: 12,
  },
  contextTitle: {
    fontSize: 15,
    fontWeight: "800",
    color: "#111827",
  },
  contextSubtitle: {
    marginTop: 4,
    fontSize: 13,
    fontWeight: "600",
    color: "#374151",
  },
  contextMeta: {
    marginTop: 5,
    fontSize: 12,
    color: "#6b7280",
    fontWeight: "500",
  },
  summaryCard: {
    backgroundColor: "#f9fafb",
    borderRadius: 12,
    padding: 12,
  },
  summaryTitle: {
    fontSize: 15,
    fontWeight: "800",
    color: "#111827",
    marginBottom: 6,
  },
  summaryLine: {
    fontSize: 13,
    color: "#374151",
    fontWeight: "600",
    marginBottom: 3,
  },
  label: {
    fontSize: 12,
    fontWeight: "700",
    color: "#374151",
    marginBottom: 6,
    marginTop: 8,
  },
  input: {
    minHeight: 44,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: "#d1d5db",
    backgroundColor: "#f9fafb",
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: "#111827",
  },
  textArea: {
    minHeight: 92,
    textAlignVertical: "top",
  },
  row: {
    flexDirection: "row",
    gap: 10,
  },
  flexField: {
    flex: 1,
  },
  smallField: {
    width: 108,
  },
  errorBox: {
    backgroundColor: "#fef2f2",
    borderWidth: 1,
    borderColor: "#fecaca",
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
  },
  errorText: {
    color: "#991b1b",
    fontWeight: "600",
    fontSize: 13,
  },
  emptyText: {
    fontSize: 14,
    color: "#6b7280",
    fontWeight: "600",
    textAlign: "center",
  },
  actionWrap: {
    marginTop: 4,
    marginBottom: 12,
  },
  primaryBtn: {
    height: 48,
    borderRadius: 14,
    backgroundColor: "#16a34a",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 10,
  },
  primaryBtnDisabled: {
    backgroundColor: "#9ca3af",
  },
  primaryBtnText: {
    color: "#ffffff",
    fontSize: 15,
    fontWeight: "800",
  },
  secondaryBtn: {
    height: 46,
    borderRadius: 14,
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#d1d5db",
    alignItems: "center",
    justifyContent: "center",
  },
  secondaryBtnText: {
    color: "#374151",
    fontSize: 14,
    fontWeight: "700",
  },
});
