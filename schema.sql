-- 1. Create Base Tables
CREATE TABLE "Product" (
    product_id integer NOT NULL,
    name character varying(50) NOT NULL,
    sell_price numeric(12,2) NOT NULL,
    units_sold integer NOT NULL,
    inventory integer NOT NULL,
    parent_id integer,
    CONSTRAINT "Product_pkey" PRIMARY KEY (product_id)
);

CREATE TABLE "Sale" (
    sale_id integer NOT NULL,
    product_id integer NOT NULL,
    date date NOT NULL,
    "time" time(0) without time zone NOT NULL,
    discount numeric(12,2) NOT NULL,
    email character varying(256),
    CONSTRAINT "Sale_pkey" PRIMARY KEY (sale_id)
);

CREATE TABLE "Material" (
    material_name character varying NOT NULL,
    product_id integer NOT NULL,
    material_cost numeric(12,2) NOT NULL,
    unit_term character varying NOT NULL,
    unit_quantity numeric(4,0) NOT NULL,
    used_quantity numeric(4,0) NOT NULL,
    CONSTRAINT "Material_pkey" PRIMARY KEY (material_name, product_id)
);

CREATE TABLE "Tag" (
    tag_id integer NOT NULL,
    label character varying NOT NULL,
    CONSTRAINT "Tag_pkey" PRIMARY KEY (tag_id)
);

CREATE TABLE "TagFor" (
    tag_id integer NOT NULL,
    product_id integer NOT NULL,
    CONSTRAINT "TagFor_pkey" PRIMARY KEY (tag_id, product_id)
);

-- 2. Create History Tables (Inheriting from Base Tables)
CREATE TABLE "ProductHistory" (
    change_id integer NOT NULL,
    change_date date NOT NULL,
    change_time timestamp(0) without time zone NOT NULL,
    CONSTRAINT "ProductHistory_pkey" PRIMARY KEY (change_id)
) INHERITS ("Product");

CREATE TABLE "SaleHistory" (
    change_id integer NOT NULL,
    change_date date NOT NULL,
    change_time time without time zone NOT NULL,
    CONSTRAINT "SaleHistory_pkey" PRIMARY KEY (change_id)
) INHERITS ("Sale");

CREATE TABLE "MaterialHistory" (
    change_id integer NOT NULL,
    change_date date NOT NULL,
    change_time time without time zone NOT NULL,
    CONSTRAINT "MaterialHistory_pkey" PRIMARY KEY (change_id)
) INHERITS ("Material");

CREATE TABLE "TagHistory" (
    change_id integer NOT NULL,
    change_date date NOT NULL,
    change_time time without time zone NOT NULL,
    CONSTRAINT "TagHistory_pkey" PRIMARY KEY (change_id)
) INHERITS ("Tag");

-- 3. Add Foreign Key Constraints
ALTER TABLE "Material" ADD CONSTRAINT "Material_product_id_fkey" FOREIGN KEY (product_id) REFERENCES "Product"(product_id);
ALTER TABLE "Product" ADD CONSTRAINT "Product_parent_id_fkey" FOREIGN KEY (parent_id) REFERENCES "Product"(product_id);
ALTER TABLE "Sale" ADD CONSTRAINT "Sale_product_id_fkey" FOREIGN KEY (product_id) REFERENCES "Product"(product_id);
ALTER TABLE "TagFor" ADD CONSTRAINT "TagFor_product_id_fkey" FOREIGN KEY (product_id) REFERENCES "Product"(product_id);
ALTER TABLE "TagFor" ADD CONSTRAINT "TagFor_tag_id_fkey" FOREIGN KEY (tag_id) REFERENCES "Tag"(tag_id);
