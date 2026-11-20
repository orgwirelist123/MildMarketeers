# Sales integration

## Registration
POST /api/productPage/registerSale/{productId} accepts:
- quantity: positive integer
- unitPrice: price per unit BEFORE discount
- discount: dollar amount deducted ONCE from the entire sale
- email: optional

Total = quantity * unitPrice - discount.

Registration reads current stock from the database and saves the sale,
stock update, product history, and sale history in one transaction.
The separate PUT /api/sales/update/{productId} endpoint has been removed.

## Database compatibility
Sale and SaleHistory require:
- quantity: integer
- unit_price: numeric(12,2)
- discount: numeric(12,2)

Earlier registration code stored discounted unit prices. Existing records
must be reviewed and converted before using the new total calculation.
Do not subtract the discount again from an already-discounted unit price.

For verified legacy records, the pre-discount unit price can be recovered as:
old unit_price + discount / quantity.

Verify positive quantities, exact cent precision, supported price range,
and unchanged final totals before applying any conversion. Update matching
history records too. Do not run a conversion twice or apply it to new-format
records. Stop old registration code during the transition.

Changing schema.sql alone does not update an existing database.

## Verification
Passed 10 WholeSaleRequestTest tests and 4 WholeSaleRepositoryTest tests,
with no failures or skips. Database tests cover successful saving and
history, insufficient stock, missing products, and rollback after an error.

Browser checks confirmed quantities, decimal totals, total sorting,
whole-sale discount calculation, stock updates, and excessive-discount
validation.
