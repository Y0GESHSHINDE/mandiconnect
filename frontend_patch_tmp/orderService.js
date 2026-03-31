import axios from "axios";
import { BASE_URL } from "../config/apiConfig";

const resolveApiBaseUrl = () => {
  const configuredUrl =
    BASE_URL || process.env.EXPO_PUBLIC_API_URL || process.env.EXPO_PUBLIC_API_BASE_URL;

  if (!configuredUrl) {
    throw new Error("API base URL is missing. Set EXPO_PUBLIC_API_URL.");
  }

  return String(configuredUrl).replace(/\/+$/, "");
};

const getErrorMessage = (error, fallbackMessage = "Request failed.") => {
  if (typeof error === "string") return error;

  const apiMessage =
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.response?.data;

  if (typeof apiMessage === "string" && apiMessage.trim()) {
    return apiMessage;
  }

  return error?.message || fallbackMessage;
};

const buildHeaders = (token) => {
  if (!token) {
    throw new Error("Authentication token is required.");
  }

  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
};

const request = async ({ method, path, token, data, fallbackMessage }) => {
  const baseUrl = resolveApiBaseUrl();

  try {
    const response = await axios({
      method,
      url: `${baseUrl}${path}`,
      data,
      headers: buildHeaders(token),
    });

    return response?.data;
  } catch (error) {
    throw new Error(getErrorMessage(error, fallbackMessage));
  }
};

const normalizeEvent = (event) => {
  if (!event || typeof event !== "object") return null;

  return {
    ...event,
    type: String(event.type || ""),
    orderStatus: String(event.orderStatus || ""),
    paymentStatus: String(event.paymentStatus || ""),
    deliveryStatus: String(event.deliveryStatus || ""),
    actionByUserId: event.actionByUserId ? String(event.actionByUserId) : "",
    actionByUserType: String(event.actionByUserType || ""),
    note: event.note || "",
    createdAt: event.createdAt || null,
  };
};

export const normalizeOrder = (order) => {
  if (!order || typeof order !== "object") return null;

  const id = order.id ?? order._id;
  if (!id) return null;

  return {
    ...order,
    id: String(id),
    orderCode: order.orderCode ? String(order.orderCode) : "",
    connectionId: order.connectionId ? String(order.connectionId) : "",
    chatId: order.chatId ? String(order.chatId) : "",
    contextType: String(order.contextType || "").toUpperCase(),
    contextRefId: order.contextRefId ? String(order.contextRefId) : "",
    buyerId: order.buyerId ? String(order.buyerId) : "",
    farmerId: order.farmerId ? String(order.farmerId) : "",
    status: String(order.status || "").toUpperCase(),
    paymentStatus: String(order.paymentStatus || "").toUpperCase(),
    deliveryStatus: String(order.deliveryStatus || "").toUpperCase(),
    currency: String(order.currency || "INR").toUpperCase(),
    subtotalAmount: Number(order.subtotalAmount || 0),
    totalAmount: Number(order.totalAmount || 0),
    item: order.item || null,
    buyer: order.buyer || null,
    farmer: order.farmer || null,
    deliveryDetails: order.deliveryDetails || null,
    notes: order.notes || "",
    events: Array.isArray(order.events) ? order.events.map(normalizeEvent).filter(Boolean) : [],
    placedAt: order.placedAt || null,
    createdAt: order.createdAt || null,
    updatedAt: order.updatedAt || null,
  };
};

export const createOrder = async (payload, token) => {
  if (!payload?.connectionId) throw new Error("connectionId is required.");
  if (!payload?.chatId) throw new Error("chatId is required.");
  if (!payload?.cropListingId) throw new Error("cropListingId is required.");

  const data = await request({
    method: "post",
    path: "/orders",
    token,
    data: {
      connectionId: String(payload.connectionId),
      chatId: String(payload.chatId),
      cropListingId: String(payload.cropListingId),
      quantity: Number(payload.quantity),
      unit: payload.unit ? String(payload.unit) : undefined,
      agreedPrice: Number(payload.agreedPrice),
      currency: payload.currency ? String(payload.currency).toUpperCase() : "INR",
      notes: payload.notes ? String(payload.notes) : undefined,
      deliveryDetails: payload.deliveryDetails || {},
    },
    fallbackMessage: "Failed to create order.",
  });

  return normalizeOrder(data);
};

export const getOrdersByChat = async (chatId, token) => {
  if (!chatId) throw new Error("chatId is required.");

  const data = await request({
    method: "get",
    path: `/orders/chat/${encodeURIComponent(String(chatId))}`,
    token,
    fallbackMessage: "Failed to load chat orders.",
  });

  return Array.isArray(data) ? data.map(normalizeOrder).filter(Boolean) : [];
};

export const getOrderById = async (orderId, token) => {
  if (!orderId) throw new Error("orderId is required.");

  const data = await request({
    method: "get",
    path: `/orders/${encodeURIComponent(String(orderId))}`,
    token,
    fallbackMessage: "Failed to load order.",
  });

  return normalizeOrder(data);
};

export const getLatestOrder = (orders) => {
  if (!Array.isArray(orders) || !orders.length) return null;
  return orders[0] || null;
};

export const orderUtils = {
  normalizeOrder,
  getLatestOrder,
};
