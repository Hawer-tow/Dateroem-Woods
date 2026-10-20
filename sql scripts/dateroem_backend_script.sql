  /*
Creation order below is dependency-safe (every FK target already exists
by the time it's referenced). USERS is created right after ROLE (its only
dependency) so every later table can safely FK its Deleted_By to USERS.
Exception: ROLE has no FK on Deleted_By since it's created before USERS.

ROLE, USERS, FARMER, REMAINDER_TREES, TRIP, VEHICLE, MACHINE, SMS_QUEUE,
CAMPAIGN, TREES, FINANCIAL_TRANSACTION, PRODUCTS, ORDERS, PROFIT,
CUSTOMER_PROFILES, EVENTS, AUDIT_LOGS, CAMPAIGN_AUDIENCE, CAMPAIGN_LOGS,
FARMER_PAYMENTS, SALARIES, TRANSPORT, COMMUNICATIONS, MACHINE_EXPENSES,
ACCOMODATION, OTHER_EXPENSES, PERMITS, BULK_DELIVERY, ORDER_ITEMS,
INVENTORY_MOVEMENT, PRODUCT_PAYMENTS, LIGHT_DELIVERIES, CAPITAL, LOAN,
BULK_TRIP_PRODUCTION_DESCRIPTION, LOAN_REPAYMENTS
*/

#///////////////////////////////////// all the tables  ///////////////////////////////////////////////////////////
####################  ROLE
create table ROLE(
Role_ID int auto_increment,
Name varchar(250),
Created_At datetime,
Update_At datetime,

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Role_ID)
);

####################  USERS
create table USERS(
User_ID int auto_increment,
User_Name varchar(100),
Name varchar(250),
Password varchar(250),
Email varchar(250),
Phone_NO varchar(250),
Bank_account Varchar(250),
Status boolean,

Role_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (User_ID),
FOREIGN KEY (Role_ID) REFERENCES ROLE(Role_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  FARMER
create table FARMER(
Farmer_ID int auto_increment,
Name varchar(250),
Phone_NO varchar(250),
Bank_Details_Marked varchar(250),
Balance decimal(10,2),

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Farmer_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  REMAINDER_TREES
create table REMAINDER_TREES(
Remainder_Trees_ID int auto_increment,
Previous_Trip_ID int,
NO_Trees int,
Type_Trees varchar(250),
Total_Body_Feet decimal(10,2),

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Remainder_Trees_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  TRIP
create table TRIP(
Trip_ID int auto_increment,
Start_Date date,
End_Date date,
NO_of_Deliveries int,
Total_Payment decimal(10,2),
Hard_Cash decimal(10,2),
Soft_Cash decimal(10,2),
Product_Description varchar(250),
Total_Body_Feet decimal(10,2),

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  VEHICLE
create table VEHICLE(
Vehicle_ID int auto_increment,
Unique_Identifier varchar(250) unique,
Driver varchar(250),
Phone_Number varchar(250),
Description varchar(250),

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Vehicle_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  MACHINE
create table MACHINE(
Machine_ID int auto_increment,
Name varchar(250),
Serial_NO varchar(250),
Description varchar(250),

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Machine_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  SMS_QUEUE
create table SMS_QUEUE(
Sms_Queue_ID int auto_increment,
Sim_Slot int,
Mpesa_Reference_ID varchar(250),
Raw_Sms_Text varchar(250),
Amount decimal(10,2),
Transaction_Type varchar(250),
Mpesa_Balance decimal(10,2),
Status varchar(250),
Created_At date,
Received_At date,

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Sms_Queue_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  CAMPAIGN
create table CAMPAIGN(
Campaign_ID int auto_increment,
Name varchar(250),
Channel varchar(250),
Start_At date,
End_At date,
Status varchar(250),

Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Campaign_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  TREES
create table TREES (
Trees_ID int not null auto_increment,
House_Hold varchar(250),
Coordinates varchar(250),
No_Trees int,
Type_Trees varchar(250),
Active boolean,

Remainder_Trees_ID int,
Farmer_ID int,
Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Trees_ID),
FOREIGN KEY (Remainder_Trees_ID) REFERENCES REMAINDER_TREES(Remainder_Trees_ID),
FOREIGN KEY (Farmer_ID) REFERENCES FARMER(Farmer_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  FINANCIAL_TRANSACTION
create table FINANCIAL_TRANSACTION(
Financial_Transaction_ID int auto_increment,
Payment_Mode varchar(250),
Reference_ID varchar(250),
Category varchar(250),
Amount decimal(10,2),
Target_ID int,
Created_At date,

Sms_Queue_ID int,
Assigned_By int,

Transaction_Cost int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Financial_Transaction_ID),
FOREIGN KEY (Sms_Queue_ID) REFERENCES SMS_QUEUE(Sms_Queue_ID),
FOREIGN KEY (Assigned_By) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  PRODUCTS
create table PRODUCTS(
Product_ID int auto_increment,
Name varchar(250),
Ske varchar(250),
Type varchar(250),
Description varchar(250),
Price decimal(10,2),
Stock decimal(10,2),
Attributes varchar(250),
Image varchar(250),

Trees_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Product_ID),
FOREIGN KEY (Trees_ID) REFERENCES TREES(Trees_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  ORDERS
create table ORDERS(
Order_ID int auto_increment,
Total int,
Status varchar(250),
Created_At date,
Hotspot_City varchar(250),

User_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Order_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  PROFIT
create table PROFIT(
Profit_ID int auto_increment,
Expencted_Amount decimal(10,2),
Recorded_Amount decimal(10,2),
Deccription varchar(250),

Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Profit_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  CUSTOMER_PROFILES
create table CUSTOMER_PROFILES(
Segment varchar(250),
Lifetime_Value int,
Last_Active date,

User_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (User_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  EVENTS
create table EVENTS(
Event_ID int auto_increment,
Event_Type varchar(250),
Payload varchar(250),
Created_At date,

User_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Event_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  AUDIT_LOGS
create table AUDIT_LOGS(
Audit_log_ID int auto_increment,
Target_Table varchar(250),
Target_Id int,
Payload varchar(250),
Created_At date,

Actor_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Audit_log_ID),
FOREIGN KEY (Actor_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  CAMPAIGN_AUDIENCE
create table CAMPAIGN_AUDIENCE(
Assigned_At date,

User_ID int,
Campaign_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Campaign_ID, User_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Campaign_ID) REFERENCES CAMPAIGN(Campaign_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  CAMPAIGN_LOGS
create table CAMPAIGN_LOGS(
Campaign_log_ID int auto_increment,
Channel varchar(250),
Status varchar(250),
Sent_At date,

User_ID int,
Campaign_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Campaign_log_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Campaign_ID) REFERENCES CAMPAIGN(Campaign_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  FARMER_PAYMENTS
create table FARMER_PAYMENTS(
Farmer_Payments_ID INT auto_increment,
Farmer_ID int,
Reference_ID varchar(250),
Amount decimal(10,2),
Method varchar(250),
Recorded_At datetime,
Description varchar(250),
refunded boolean,

Recorder_By int,
Financial_Transaction_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Farmer_Payments_ID),
FOREIGN KEY (Recorder_By) REFERENCES USERS(User_ID),
FOREIGN KEY (Farmer_ID) REFERENCES FARMER(Farmer_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  SALARIES
create table SALARIES(
Salary_ID int auto_increment,
Amount decimal(10,2),
unique_Salary_Id varchar(250),
Salary_Type varchar(250),
Type varchar(250),
Description varchar(250),
Created_At boolean,

Financial_Transaction_ID int,
Trip_ID int,
User_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Salary_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  TRANSPORT
create table TRANSPORT(
Transport_ID int auto_increment,
Additional_ID varchar(100),
Amount decimal(10,2),
Type varchar(250),
Reference_ID varchar(250),
Description varchar(250),

User_ID int,
Vehicle_Unique_ID varchar(250),
Financial_Transaction_ID int,
Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Transport_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Vehicle_Unique_ID) REFERENCES VEHICLE(Unique_Identifier),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  COMMUNICATIONS
create table COMMUNICATIONS(
Communications_ID int auto_increment,
Amount decimal(10,2),
Description varchar(250),
Reference_ID varchar(250),
type varchar(250),

User_ID int,
Trip_ID int,
Financial_Transaction_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Communications_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  MACHINE_EXPENSES
create table MACHINE_EXPENSES(
Machine_Expenses_ID int auto_increment,
Type varchar(250),
Amount decimal(10,2),
Date_Of_Expense datetime,
Reference_ID varchar(250),

Machine_ID int,
Financial_Transaction_ID int,
Trip_ID int,
User_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Machine_Expenses_ID),
FOREIGN KEY (Machine_ID) REFERENCES MACHINE(Machine_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  ACCOMODATION
create table ACCOMODATION(
Accomodation_ID int auto_increment,
Amount decimal(10,2),
Reference_ID varchar(250),
Description varchar(250),
Type varchar(250),
Created_At datetime,

User_ID int,
Trip_ID int,
Financial_Transaction_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Accomodation_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  OTHER_EXPENSES
create table OTHER_EXPENSES(
Other_Expenses_ID int auto_increment,
Amount decimal(10,2),
Reference_ID varchar(250),
Description varchar(250),

Trip_ID int,
User_ID int,
Financial_Transaction_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Other_Expenses_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  PERMITS
create table PERMITS(
Permit_ID int auto_increment,
Serial_NO varchar(250),
Amount decimal(10,2),
Reference_ID varchar(250),
Start_Date date,
End_Date date,

Trip_ID int,
Financial_Transaction_ID int,
User_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Permit_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  BULK_DELIVERY
create table BULK_DELIVERY(
Bulk_Delivery_ID int auto_increment,
Amount_Paid decimal(10,2),
Reference_ID varchar(250),
Delivered_To varchar(250),
Delivered_At date,
Description varchar(250),

Financial_Transaction_ID int,
Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Bulk_Delivery_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  ORDER_ITEMS
create table ORDER_ITEMS(
Order_Items_ID int auto_increment,
Qty int,
Unit_Price decimal(10,2),

Order_ID int,
Product_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Order_Items_ID),
FOREIGN KEY (Order_ID) REFERENCES ORDERS(Order_ID),
FOREIGN KEY (Product_ID) REFERENCES PRODUCTS(Product_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  INVENTORY_MOVEMENT
create table INVENTORY_MOVEMENT(
Inventory_Movement_ID int auto_increment,
Changes varchar(250),
Reason varchar(250),
Reference_ID varchar(250),
Created_At date,

Product_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Inventory_Movement_ID),
FOREIGN KEY (Product_ID) REFERENCES PRODUCTS(Product_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  PRODUCT_PAYMENTS
create table PRODUCT_PAYMENTS(
Product_Payment_ID int auto_increment,
Provided varchar(250),
Status varchar(250),
Amount decimal(10,2),
Provider_Ref varchar(250),
Created_At date,
Description varchar(250),

Order_ID int,
Financial_Transaction_ID int,
Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Product_Payment_ID),
FOREIGN KEY (Order_ID) REFERENCES ORDERS(Order_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  lIGHT_DELIVERIES
create table LIGHT_DELIVERIES(
Light_Delivery_ID int auto_increment,
Status varchar(250),
Sta varchar(250),
Delivered_At date,
Signature_Blub varchar(250),

Order_ID int,
Worker_ID int,
Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Light_Delivery_ID),
FOREIGN KEY (Order_ID) REFERENCES ORDERS(Order_ID),
FOREIGN KEY (Worker_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  CAPITAL
create table CAPITAL(
Capital_ID int auto_increment,
Amount decimal(10,2),
Reference_ID varchar (250),
Description varchar(250),
Category varchar(250),
Created_At date,

Trip_ID int,
Capital_From_User int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Capital_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Capital_From_User) REFERENCES USERS(User_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  LOAN
create table LOAN(
Loan_ID int auto_increment,
Amount decimal(10,2),
Reference_ID varchar(250),
Description varchar(250),
Category varchar(250),
Status varchar(250),

Trip_ID int,
User_ID int,
Financial_Transaction_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Loan_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (User_ID) REFERENCES USERS(User_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  BULK_TRIP_PRODUCTION_DESCRIPTION
create table BULK_TRIP_PRODUCTION_DESCRIPTION(
Bulk_Trip_Production_Description_ID int auto_increment,
Product_Type varchar(250),
Product_Description varchar(250),
Product_Measurement varchar(250),
Total_Body_Feet decimal(10,2),
Product_Total decimal(10,2),

Bulk_Delivery_ID int,
Trip_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Bulk_Trip_Production_Description_ID),
FOREIGN KEY (Bulk_Delivery_ID) REFERENCES BULK_DELIVERY(Bulk_Delivery_ID),
FOREIGN KEY (Trip_ID) REFERENCES TRIP(Trip_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

####################  LOAN_REPAYMENTS
create table LOAN_REPAYMENTS(
Loan_Repayment_ID int auto_increment,
Amount_Paid decimal(10,2),
Payment_Date date,
Description varchar(250),
Reference_ID varchar(250),

Financial_Transaction_ID int,
Loan_ID int,
Is_Deleted boolean not null default false,
Deleted_By int,
Deleted_At datetime,

PRIMARY KEY (Loan_Repayment_ID),
FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID),
FOREIGN KEY (Loan_ID) REFERENCES LOAN(Loan_ID),
FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);
