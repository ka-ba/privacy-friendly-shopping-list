package privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.ui.products;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.R;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.framework.context.AbstractInstanceFactory;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.framework.context.InstanceFactory;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.framework.utils.MessageUtils;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.ProductService;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.domain.ProductDto;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.domain.TotalDto;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.shoppingList.business.ShoppingListService;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.shoppingList.business.domain.ListDto;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.ui.main.MainActivity;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.ui.products.listadapter.ProductsAdapter;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.ui.products.listeners.*;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.ui.settings.SettingsKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Description:
 * Author: Grebiel Jose Ifill Brito
 * Created: 09.07.16 creation date
 */
public class ProductsActivity extends AppCompatActivity
{
    public static final String PRODUCT_ID_KEY = "product.id";
    public static final String PRODUCT_NAME = "product.name";
    public static final String FROM_DIALOG = "from.dialog";
    public static final String PHOTO_BITMAP = "photo.bitmap";
    public static final String SCHEDULED_FOR_DELETION = "scheduled.for.deletion";
    public static final int REQUEST_PHOTO_PREVIEW_FROM_ITEM = 3;

    private static final long DURATION = 1000L;
    private ProductService productService;
    private ShoppingListService shoppingListService;
    private ProductActivityCache cache;
    private String listId;
    private ListDto listDto;

    @Override
    protected final void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.products_activity);

        MessageUtils.showTutorialDialog(this);

        AbstractInstanceFactory instanceFactory = new InstanceFactory(getApplicationContext());
        this.productService = (ProductService) instanceFactory.createInstance(ProductService.class);
        this.shoppingListService = (ShoppingListService) instanceFactory.createInstance(ShoppingListService.class);

        listId = getIntent().getStringExtra(MainActivity.LIST_ID_KEY);
        shoppingListService.getById(listId)
                .doOnNext(result -> listDto = result)
                .doOnCompleted(() ->
                {
                    setTitle(listDto.getListName());
                    cache = new ProductActivityCache(this, listId, listDto.getListName(), listDto.isStatisticEnabled());
                    cache.getNewListFab().setOnClickListener(new AddProductOnClickListener(cache));
                    cache.getSearchAutoCompleteTextView().addTextChangedListener(new SearchTextWatcher(cache));
                    cache.getCancelSarchButton().setOnClickListener(new CancelSearchOnClick(cache));
                    updateListView();
                })
                .subscribe();

        overridePendingTransition(0, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.products_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        MenuItem searchItem = menu.findItem(R.id.imageview_search);
        searchItem.setOnMenuItemClickListener(new ShowSearchFieldOnClickListener(this));

        MenuItem sortItem = menu.findItem(R.id.imageview_sort);
        sortItem.setOnMenuItemClickListener(new SortProductsOnClickListener(this, listId));

        MenuItem deleteItem = menu.findItem(R.id.imageview_delete);
        deleteItem.setOnMenuItemClickListener(new ShowDeleteProductsOnClickListener(this, listId));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if ( requestCode == REQUEST_PHOTO_PREVIEW_FROM_ITEM && resultCode == RESULT_OK )
        {
            updateListView();
        }
    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        updateListView();
    }

    public void updateListView()
    {
        List<ProductDto> allProducts = new ArrayList<>();

        productService.getAllProducts(cache.getListId())
                .doOnNext(dto -> allProducts.add(dto))
                .doOnCompleted(() ->
                {
                    if ( allProducts.isEmpty() )
                    {
                        cache.getNoProductsLayout().setVisibility(View.VISIBLE);
                    }
                    else
                    {
                        cache.getNoProductsLayout().setVisibility(View.GONE);
                    }

                    // sort according to last sort selection
                    final ListDto[] listDto = new ListDto[ 1 ];
                    shoppingListService.getById(listId)
                            .doOnNext(dto -> listDto[ 0 ] = dto)
                            .doOnCompleted(() ->
                            {
                                String sortBy = listDto[ 0 ].getSortCriteria();
                                boolean sortAscending = listDto[ 0 ].isSortAscending();
                                productService.sortProducts(allProducts, sortBy, sortAscending);

                                cache.getProductsAdapter().setProductsList(allProducts);
                                cache.getProductsAdapter().notifyDataSetChanged();

                                reorderProductViewBySelection();
                                updateTotals();
                            })
                            .subscribe();
                })
                .subscribe();
    }

    public void updateTotals()
    {
        TotalDto totalDto = productService.computeTotals(cache.getProductsAdapter().getProductsList());
        cache.getTotalAmountTextView().setText(totalDto.getTotalAmount());
        cache.getTotalCheckedTextView().setText(totalDto.getCheckedAmount());

        if ( totalDto.isEqualsZero() )
        {
            cache.getTotalLayout().animate().alpha(0.0f).setDuration(DURATION);
            cache.getTotalLayout().setVisibility(View.GONE);
        }
        else
        {
            cache.getTotalLayout().setVisibility(View.VISIBLE);
            cache.getTotalLayout().animate().alpha(1.0f).setDuration(DURATION);
        }
    }

    public void changeItemPosition(ProductDto dto)
    {
        if ( PreferenceManager.getDefaultSharedPreferences(cache.getActivity()).getBoolean(SettingsKeys.MOVE_PRODUCTS_PREF, true) )
        {
            ProductsAdapter productsAdapter = cache.getProductsAdapter();
            List<ProductDto> productsList = productsAdapter.getProductsList();
            List<ProductDto> productDtos = productService.moveSelectedToEnd(productsList);
            productsAdapter.setProductsList(productDtos);

            int initialPosition = productsList.indexOf(dto);
            int finalPosition = productDtos.indexOf(dto);
            productsAdapter.notifyItemMoved(initialPosition, finalPosition);
            // Animation ends in final position when the initial position is equals zero.
            // Therefore the animation needs to be fix by scrolling back to position 0.
            if ( initialPosition == 0 )
            {
                cache.getRecyclerView().scrollToPosition(0);
            }
        }
    }

    public void reorderProductViewBySelection()
    {
        if ( PreferenceManager.getDefaultSharedPreferences(cache.getActivity()).getBoolean(SettingsKeys.MOVE_PRODUCTS_PREF, true) )
        {
            ProductsAdapter productsAdapter = cache.getProductsAdapter();
            List<ProductDto> productsList = productsAdapter.getProductsList();
            List<ProductDto> productDtos = productService.moveSelectedToEnd(productsList);
            productsAdapter.setProductsList(productDtos);
            productsAdapter.notifyDataSetChanged();
        }
    }

    public void setProductsAndUpdateView(List<ProductDto> sortedProducts)
    {
        cache.getProductsAdapter().setProductsList(sortedProducts);
        cache.getProductsAdapter().notifyDataSetChanged();
    }
}
