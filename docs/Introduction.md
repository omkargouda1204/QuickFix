# 1. Introduction

## 1.1 Introduction of the System

### 1.1.1 Project Title
**QuickFix** — On-Demand Home Services Platform

### 1.1.2 Category
Android Application / Service Marketplace / E-commerce

### 1.1.3 Overview
QuickFix is a comprehensive Android application designed to bridge the gap between local service providers (like plumbers, electricians, AC technicians, etc.) and customers needing immediate household repairs or maintenance. The platform allows users to find, book, and track services based on their location, while providing a dedicated dashboard for providers to manage their business applications, services, and bookings.

## 1.2 Objectives of the System
*   To provide a seamless interface for users to search for home services based on their current location or a selected city.
*   To enable quick booking and scheduling of verified service providers.
*   To facilitate communication between customers and providers via an integrated chat system.
*   To provide a platform for local professionals to list their businesses, manage bookings, and grow their customer base.
*   To ensure service quality through a rating and review system.
*   To simplify payments using secure payment gateway integration (Razorpay).

## 1.3 Scope of the System
The system covers the following key areas:
*   **User Management:** Authentication via Firebase (Email/Password and Google Sign-In).
*   **Service Discovery:** Location-aware search and category-based browsing of service providers.
*   **Booking Engine:** Real-time scheduling, status tracking (Pending, Accepted, In-Progress, Completed), and booking history.
*   **Provider Onboarding:** Registration workflow for new businesses including identity verification and profile management.
*   **Communication:** In-app messaging between users and providers using Firebase Cloud Messaging.
*   **Location Services:** Automatic GPS-based location detection and manual city selection for service filtering.
*   **Payments:** Integration with Razorpay for secure transaction processing.
*   **AI Integration:** AI-assisted search and potentially an AI chat assistant for user queries.

## 1.4 Structure of the System
The system is structured as a multi-tier architecture:
1.  **Presentation Layer (Android App):** Built using Java and XML, providing the UI for both Customers and Providers.
2.  **Logic Layer:** Managed through various Activity and Manager classes (AppLocationManager, SupabaseClient, etc.) within the Android application.
3.  **Data Layer (Supabase):** Handles data storage, authentication, and edge functions.
4.  **Real-time Layer (Firebase):** Handles Push Notifications and real-time Authentication.

## 1.5 System Architecture
The QuickFix system utilizes a robust, multi-layered architecture based on the **Backend-as-a-Service (BaaS)** model. This approach decouples the complex frontend from the scalable backend, ensuring high availability and secure data management.

### 1.5.1 Main Layers of the System

#### 1. Users Layer
The system interacts with three distinct user roles:
*   **Customers:** Access the application to browse services, manage bookings, and make payments.
*   **Service Providers:** Professionals who register their businesses, list services, and respond to customer requests.
*   **Administrators:** Use the backend dashboard to oversee the entire platform, verify provider credentials, and moderate content.

#### 2. Authentication Layer (Firebase Auth)
Security is prioritized through a dedicated authentication layer:
*   **Multi-Auth Support:** Integration with Firebase allows for standard Email/Password credentials and Google Sign-In (OAuth 2.0).
*   **Session Management:** Secure token-based authentication ensures that user sessions are persistent and protected across the app.
*   **Role-Based Access Control (RBAC):** Authentication tokens are linked to specific roles in the database (via Row Level Security) to restrict access to provider-only or customer-only features.

#### 3. User Interface (UI) Layer
The presentation tier is divided into two primary native Android interfaces:
*   **Customer/User UI:** Features a modern home screen with service categories, a location-aware search bar, real-time booking tracking, and an integrated chat system.
*   **Provider UI:** A specialized "Provider Dashboard" where business owners can monitor earnings, manage service listings, update business profiles, and handle active bookings.

#### 4. Admin UI (Backend Dashboard)
The Administrative interface is handled via the **Supabase Dashboard**:
*   **Data Management:** Direct access to PostgreSQL tables for troubleshooting and data auditing.
*   **Approval Workflow:** Manual verification of provider identity documents and business registrations before they are marked as "Approved" for public listing.
*   **System Configuration:** Managing global parameters like service categories and platform-wide settings.

#### 5. Backend & Logic Layer
The backend is split between on-device logic and server-side execution:
*   **Android Managers:** Custom Java classes (e.g., `SupabaseClient`, `RoleManager`, `AppLocationManager`) handle data fetching, role verification, and device-side state updates.
*   **Supabase Edge Functions:** Serverless TypeScript functions (Deno) perform heavy server-side tasks, such as triggering push notifications via FCM, processing Razorpay webhooks, and running AI-assisted search logic.

#### 6. Database & Storage Layer (Supabase)
All application data and media are stored in a centralized cloud environment:
*   **PostgreSQL Database:** A relational database housing tables for Users, Provider Profiles, Services, Bookings, Reviews, and Chats. It utilizes **Row Level Security (RLS)** to enforce data privacy.
*   **Supabase Storage:** A secure object storage system for hosting business images, provider identity proofs, and user profile pictures.

#### 7. Integration & Messaging Layer (Others)
*   **Payments (Razorpay):** A secure gateway for handling digital transactions between customers and providers.
*   **Push Notifications (FCM):** Uses Firebase Cloud Messaging to send real-time alerts for new bookings, message replies, and status updates.
*   **Location Services (Google Fused Location API):** Enables precise geolocation for finding "nearby" providers and mapping service addresses.
*   **Intelligence Services (Google ML Kit):** On-device machine learning used for image labeling and automated business photo analysis.

## 1.6 End Users
1.  **Customers:** Individuals looking for home maintenance or repair services.
2.  **Service Providers:** Local businesses or freelancers providing specialized services.
3.  **Administrators (Internal):** Users who approve provider profiles and manage system configurations.

## 1.7 Software/Hardware Used for Development
*   **Operating System:** Windows/macOS.
*   **IDE:** Android Studio (Ladybug or newer).
*   **Programming Language:** Java (Android), TypeScript (Supabase Edge Functions).
*   **Version Control:** Git.
*   **Database Management:** Supabase Dashboard (PostgreSQL).
*   **Design Tools:** XML Layout Editor.

## 1.8 Software/Hardware Required for Implementation
*   **Software (Client Side):** Android OS version 7.0 (Nougat, API 24) or higher.
*   **Software (Server Side):** Supabase Account, Firebase Project, Razorpay Account.
*   **Hardware (User):** Android Smartphone with GPS and Internet connectivity.
*   **Hardware (Provider):** Android Smartphone with Camera (for profile/business image uploads).
