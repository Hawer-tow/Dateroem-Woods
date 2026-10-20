-- ============================================================================
-- Migration 032: frequently asked questions for the website's Help page
-- ============================================================================
-- "adding faqs in the help area to populate it to make it to stop feeling
-- less empty to assist in one getting common assistance from areas in the
-- site and add this faqs in the desktop for editing adding new ones so that
-- it constantly maintained based on changes done on certain features".
--
-- Each question belongs to a Topic (the website groups them by topic, in
-- the order of their Sort_Order) and may point at the page it is about
-- (Link_Path, a path on the website such as /shipping). Is_Published hides
-- a question from the website without deleting it; deleting is a soft
-- delete like everywhere else, so a question can be restored.
--
-- The first questions below are written from how the website works on
-- 7 October 2026 (the same facts the website's assistant is given). Edit
-- them in the desktop app (FAQs) whenever a feature changes.
-- ============================================================================

CREATE TABLE FAQS (
  Faq_ID INT NOT NULL AUTO_INCREMENT,
  Topic VARCHAR(40) NOT NULL,
  Question VARCHAR(250) NOT NULL,
  Answer TEXT NOT NULL,
  Link_Path VARCHAR(120) NULL,
  Sort_Order INT NOT NULL DEFAULT 100,
  Is_Published BOOLEAN NOT NULL DEFAULT TRUE,
  Created_At DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  Updated_At DATETIME NULL,
  Updated_By INT NULL,
  Is_Deleted BOOLEAN NOT NULL DEFAULT FALSE,
  Deleted_By INT NULL,
  Deleted_At DATETIME NULL,
  PRIMARY KEY (Faq_ID),
  KEY idx_faqs_listing (Is_Deleted, Is_Published, Sort_Order),
  CONSTRAINT fk_faqs_updated_by FOREIGN KEY (Updated_By) REFERENCES USERS (User_ID),
  CONSTRAINT fk_faqs_deleted_by FOREIGN KEY (Deleted_By) REFERENCES USERS (User_ID)
);

INSERT INTO FAQS (Topic, Question, Answer, Link_Path, Sort_Order) VALUES
('Ordering', 'Do I need an account to order?',
 'You can browse and fill your cart without one, but placing an order needs a free account. At checkout, sign in or create one with the account icon at the top right.',
 '/cart', 10),
('Ordering', 'How do I place an order?',
 '1. Add the products you want to your cart.\n2. Open the cart and choose the pickup point where you will collect your order.\n3. Enter and confirm your Safaricom M-Pesa number (07XX or 01XX).\n4. When the M-Pesa prompt appears on that phone, enter your M-Pesa PIN.\nYour order is placed as soon as M-Pesa confirms the payment.',
 '/cart', 20),
('Ordering', 'I need a large quantity. Can I still order?',
 'Yes. The stock shown is what can be bought online right now. For bulk or heavy orders, use the Bulk Orders page and the team will arrange your order with you directly.',
 '/heavy-order', 30),
('Ordering', 'Can I change or cancel an order after paying?',
 'Changes, cancellations, returns and refunds of paid orders are handled by the team. Contact them with the email, call or WhatsApp buttons at the top of this page.',
 NULL, 40),
('Payment', 'How do I pay?',
 'With M-Pesa, upfront. After you confirm your number at checkout, an M-Pesa prompt pops up on that phone and you enter your PIN to pay. There is no cash payment and no pay on delivery.',
 NULL, 110),
('Payment', 'Is there a delivery fee?',
 'No. Checkout charges only the total for your products; the website adds no delivery fee.',
 NULL, 120),
('Payment', 'What if my payment fails or I cancel the M-Pesa prompt?',
 'The order is cancelled automatically and the stock is released. Your cart is kept, so you can simply try again.',
 '/cart', 130),
('Delivery and pickup', 'Where do I collect my order?',
 'At the pickup point you chose at checkout, within its collection hours. The Shipping page lists every pickup point by town, with its hours.',
 '/shipping', 210),
('Delivery and pickup', 'How long does delivery take?',
 'An order of normal-sized items is estimated at about 3 weeks after ordering; an order with very large items (over 500 board feet each) at about 3 months. Once the team schedules your order on a delivery run, the confirmed date shows on the Ordered Goods page.',
 '/my-orders', 220),
('Delivery and pickup', 'How do I check on my order?',
 'Sign in and open Ordered Goods to see each of your orders with its payment and delivery status. While signed in, you can also ask the chat assistant about your orders.',
 '/my-orders', 230),
('Account and sign-in', 'How do I create an account?',
 'Select the account icon at the top right, then Create account. Choose your own username (3 to 30 characters, no spaces or @) and fill in your full name, a password and your phone number; an email address is optional. You can also continue with Google.',
 NULL, 310),
('Account and sign-in', 'Can I sign in with my email address?',
 'Yes. Sign in with your username or your email address, and your password. If more than one account uses the same email address, sign in with your username instead.',
 NULL, 320),
('Ratings', 'How do I rate a product?',
 'Once your goods are delivered, open Ordered Goods and rate each product from 1 to 5 stars, with a comment if you like. You can change or remove your rating there later.',
 '/my-orders', 410),
('Ratings', 'Who can see my rating?',
 'Your rating is private unless you tick "public" and accept the agreement; then only your first name and initial are shown. A product shows its stars once at least 3 buyers have rated it.',
 NULL, 420),
('Offers', 'What is on the Featured page?',
 'The offers running right now and the products they include. The Featured link appears in the menu only while an offer is running.',
 '/featured', 510),
('About Dateroem', 'Where does your timber come from?',
 'The About page tells the story of Dateroem and where its timber comes from.',
 '/about', 610);
